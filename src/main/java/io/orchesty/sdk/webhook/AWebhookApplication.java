package io.orchesty.sdk.webhook;

import io.orchesty.sdk.application.AApplication;
import io.orchesty.sdk.application.enums.ApplicationType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for applications that expose webhooks.
 * Subclasses implement the API subscribe/unsubscribe specifics and the {@link #getWebhookSubscriptions()}
 * catalog; this class drives the lifecycle through the sync UI actions
 * ({@code listWebhookEvents} / {@code subscribeWebhook} / {@code unsubscribeWebhook}).
 */
public abstract class AWebhookApplication extends AApplication implements IWebhookApplication {

    /** Sync-action names exposed to the UI (see {@code /applications/{name}/sync/{method}}). */
    public static final List<String> SYNC_ACTIONS =
            List.of("listWebhookEvents", "subscribeWebhook", "unsubscribeWebhook");

    private WebhookManager webhookManager;

    @Override
    public String getApplicationType() {
        return ApplicationType.WEBHOOK;
    }

    public AWebhookApplication setWebhookManager(WebhookManager manager) {
        this.webhookManager = manager;
        return this;
    }

    @Override
    public List<String> getSyncMethods() {
        return SYNC_ACTIONS;
    }

    @Override
    public Object runSynchronousAction(String method, Map<String, Object> body) {
        switch (method) {
            case "listWebhookEvents":
                return syncListWebhookEvents();
            case "subscribeWebhook":
                return syncSubscribeWebhook(body);
            case "unsubscribeWebhook":
                return syncUnsubscribeWebhook(body);
            default:
                return super.runSynchronousAction(method, body);
        }
    }

    /**
     * Lists the webhook events the UI can offer when configuring a Webhook node.
     *
     * @return one entry per subscription with its {@code name}, {@code parameters} and {@code description}
     */
    public List<Map<String, Object>> syncListWebhookEvents() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WebhookSubscription subs : getWebhookSubscriptions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", subs.getName());
            item.put("parameters", subs.getParameters());
            item.put("description", subs.getDescription());
            out.add(item);
        }
        return out;
    }

    /**
     * Subscribes a single webhook event.
     *
     * @param body request body {@code {name, topology, node, parameters?, user, sdk}}
     * @return a status map with the subscribed webhooks
     */
    public Map<String, Object> syncSubscribeWebhook(Map<String, Object> body) {
        WebhookManager manager = requireManager();
        String[] userSdk = requireUserSdk(body);
        List<Webhook> result = manager.subscribeWebhooks(getName(), userSdk[0], userSdk[1], body);
        return okWithWebhooks(result);
    }

    /**
     * Unsubscribes a single webhook event.
     *
     * @param body request body {@code {name, topology, node, user, sdk}}
     * @return a status map with the unsubscribed webhooks
     */
    public Map<String, Object> syncUnsubscribeWebhook(Map<String, Object> body) {
        WebhookManager manager = requireManager();
        String[] userSdk = requireUserSdk(body);
        List<Webhook> result = manager.unsubscribeWebhooks(getName(), userSdk[0], userSdk[1], body);
        return okWithWebhooks(result);
    }

    private WebhookManager requireManager() {
        if (webhookManager == null) {
            throw new IllegalStateException(
                    "WebhookManager has not been injected into application [" + getName() + "].");
        }
        return webhookManager;
    }

    private static String[] requireUserSdk(Map<String, Object> body) {
        Object user = body.get("user");
        Object sdk = body.get("sdk");
        if (user == null
                || sdk == null
                || String.valueOf(user).isEmpty()
                || String.valueOf(sdk).isEmpty()) {
            throw new IllegalArgumentException("Sync webhook action requires `user` and `sdk` in the body.");
        }
        return new String[] {String.valueOf(user), String.valueOf(sdk)};
    }

    private static Map<String, Object> okWithWebhooks(List<Webhook> result) {
        List<Map<String, Object>> webhooks = new ArrayList<>();
        for (Webhook w : result) {
            if (w != null) {
                webhooks.add(w.toArray());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("webhooks", webhooks);
        return out;
    }
}
