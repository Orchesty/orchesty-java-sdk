package io.orchesty.sdk.webhook;

import io.orchesty.sdk.application.AApplication;
import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.ApplicationInstallRepository;
import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.application.enums.ApplicationType;
import io.orchesty.sdk.transport.HttpSender;
import io.orchesty.sdk.transport.RequestDto;
import io.orchesty.sdk.transport.ResponseDto;
import io.orchesty.sdk.transport.ResultRanges;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes and unsubscribes webhooks against the third-party API and persists them.
 * Builds the callback URL from the starting-point host, delegates request/response shaping to the
 * application, and stores {@link Webhook} documents via the {@link WebhookRepository}.
 */
public class WebhookManager {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookManager.class);
    private static final int TOKEN_BYTES = 25;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, IApplication> applications;
    private final HttpSender httpSender;
    private final WebhookRepository webhookRepository;
    private final ApplicationInstallRepository appRepository;
    private final String startingPoint;

    public WebhookManager(
            Map<String, IApplication> applications,
            HttpSender httpSender,
            WebhookRepository webhookRepository,
            ApplicationInstallRepository appRepository,
            String startingPoint) {
        this.applications = applications;
        this.httpSender = httpSender;
        this.webhookRepository = webhookRepository;
        this.appRepository = appRepository;
        this.startingPoint = startingPoint == null ? "" : startingPoint;
    }

    public String getWebhookUrl(String topology, String node, String token) {
        return startingPoint.replaceAll("/+$", "") + "/topologies/" + topology + "/nodes/" + node + "/token/" + token
                + "/run";
    }

    /**
     * Builds the UI-facing view of the subscription catalog merged with persisted webhooks.
     *
     * @param app webhook application whose subscriptions are listed
     * @param user user the webhooks belong to
     * @param sdk SDK the webhooks belong to
     * @return one form entry per subscription, or per persisted webhook if any exist
     */
    public List<Map<String, Object>> getWebhooks(AApplication app, String user, String sdk) {
        List<Webhook> webhooks = getAllWebhooks(app.getName(), user, sdk);
        List<Map<String, Object>> out = new ArrayList<>();

        for (WebhookSubscription subs : ((IWebhookApplication) app).getWebhookSubscriptions()) {
            List<Webhook> filtered = new ArrayList<>();
            for (Webhook w : webhooks) {
                if (w.getName().equals(subs.getName())) {
                    filtered.add(w);
                }
            }
            if (filtered.isEmpty()) {
                out.add(webhookForm(
                        subs.getName(),
                        !subs.getTopology().isEmpty(),
                        false,
                        subs.getTopology(),
                        subs.getNode(),
                        "",
                        ""));
                continue;
            }
            for (Webhook w : filtered) {
                out.add(webhookForm(
                        subs.getName(),
                        !subs.getTopology().isEmpty(),
                        true,
                        w.getTopology(),
                        w.getNode(),
                        w.getWebhookId(),
                        w.getToken()));
            }
        }
        return out;
    }

    public List<Webhook> subscribeWebhooks(String name, String user, String sdk, Map<String, Object> data) {
        validateBody(data);
        IWebhookApplication app = getApplication(name);
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);
        if (!ApplicationType.isWebhook(app.getApplicationType()) || !app.isAuthorized(install)) {
            return List.of();
        }

        List<Webhook> result = new ArrayList<>();
        for (WebhookSubscription subs : app.getWebhookSubscriptions()) {
            if (subs.getTopology().isEmpty() && !subs.getName().equals(str(data.get("name")))) {
                continue;
            }
            String topology = data.get("topology") != null ? str(data.get("topology")) : subs.getTopology();
            String node = data.get("node") != null ? str(data.get("node")) : subs.getNode();
            if (topology.isEmpty() || node.isEmpty()) {
                throw new IllegalArgumentException("Cannot subscribe webhook [" + subs.getName() + "] of application ["
                        + name + "]: topology and node must be supplied by WebhookSubscription or request body.");
            }
            WebhookSubscription requested = withRequestParameters(subs, data);

            String token = randomToken();
            RequestDto request =
                    app.getWebhookSubscribeRequestDto(install, requested, getWebhookUrl(topology, node, token));
            // Default ranges: a non-2xx response throws before anything is saved.
            ResponseDto response = httpSender.send(request);
            String webhookId = app.processWebhookSubscribeResponse(response, install);

            Webhook webhook = new Webhook()
                    .setName(subs.getName())
                    .setUser(user)
                    .setSdk(sdk)
                    .setNode(node)
                    .setTopology(topology)
                    .setApplication(app.getName())
                    .setWebhookId(webhookId)
                    .setToken(token);
            webhookRepository.insert(webhook);
            result.add(webhook);
        }
        return result;
    }

    public List<Webhook> unsubscribeWebhooks(String name, String user, String sdk, Map<String, Object> data) {
        validateBody(data);
        IWebhookApplication app = getApplication(name);
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);
        if (!ApplicationType.isWebhook(app.getApplicationType()) || !app.isAuthorized(install)) {
            return List.of();
        }

        List<Webhook> result = new ArrayList<>();
        for (Webhook webhook : getAllWebhooks(name, user, sdk)) {
            if (!matchWebhook(webhook, data)) {
                continue;
            }
            RequestDto request = app.getWebhookUnsubscribeRequestDto(install, webhook);
            ResponseDto response = httpSender.send(request, ResultRanges.KEEP_ALL);
            if (app.processWebhookUnsubscribeResponse(response)) {
                webhookRepository.remove(webhook);
            } else {
                webhook.setUnsubscribeFailed(true);
                webhookRepository.update(webhook);
                LOG.warn(
                        "[webhook] Unsubscribe failed for [{}] (topology={}, node={}, externalId={})",
                        webhook.getName(),
                        webhook.getTopology(),
                        webhook.getNode(),
                        webhook.getWebhookId());
            }
            result.add(webhook);
        }
        return result;
    }

    private boolean matchWebhook(Webhook webhook, Map<String, Object> data) {
        if (data.get("name") != null && !webhook.getName().equals(str(data.get("name")))) {
            return false;
        }
        if (data.get("topology") != null && !webhook.getTopology().equals(str(data.get("topology")))) {
            return false;
        }
        return data.get("node") == null || webhook.getNode().equals(str(data.get("node")));
    }

    private List<Webhook> getAllWebhooks(String application, String user, String sdk) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("apps", List.of(application));
        filter.put("users", List.of(user));
        filter.put("sdks", List.of(sdk));
        return webhookRepository.findMany(filter);
    }

    private IWebhookApplication getApplication(String key) {
        IApplication app = applications.get(key);
        if (!(app instanceof IWebhookApplication webhookApp)) {
            throw new IllegalArgumentException("Application [" + key + "] is not a webhook application.");
        }
        return webhookApp;
    }

    private ApplicationInstall loadApplicationInstall(String name, String user, String sdk) {
        ApplicationInstall install = appRepository.findByNameAndUser(name, user, List.of(sdk), null);
        if (install == null) {
            throw new IllegalStateException(
                    "ApplicationInstall with user [" + user + "] and name [" + name + "] has not been found!");
        }
        return install;
    }

    private static void validateBody(Map<String, Object> data) {
        if (data.get("name") == null && data.get("topology") == null && data.get("node") == null) {
            throw new IllegalArgumentException("Required parameter [name, topology, node] not found.");
        }
    }

    /**
     * Returns a per-call copy of the subscription with the request's {@code parameters} merged in.
     * The application's own subscription (and its parameter map) is never modified.
     *
     * @param subs subscription from the application's catalog
     * @param data request body
     * @return a copy of the subscription with the merged parameters
     */
    private static WebhookSubscription withRequestParameters(WebhookSubscription subs, Map<String, Object> data) {
        Map<String, String> parameters = new LinkedHashMap<>(subs.getParameters());
        if (data.get("parameters") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> parameters.put(String.valueOf(k), String.valueOf(v)));
        }
        return new WebhookSubscription(
                subs.getName(), subs.getNode(), subs.getTopology(), parameters, subs.getDescription());
    }

    private static Map<String, Object> webhookForm(
            String name,
            boolean isDefault,
            boolean enabled,
            String topology,
            String node,
            String webhookId,
            String token) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", name);
        form.put("default", isDefault);
        form.put("enabled", enabled);
        form.put("topology", topology);
        form.put("node", node);
        form.put("webhookId", webhookId);
        form.put("token", token);
        return form;
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
