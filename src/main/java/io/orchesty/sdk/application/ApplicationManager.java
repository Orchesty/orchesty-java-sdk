package io.orchesty.sdk.application;

import io.orchesty.sdk.application.action.CustomAction;
import io.orchesty.sdk.application.enums.ApplicationType;
import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.authorization.IOAuth2Application;
import io.orchesty.sdk.authorization.OAuth2Provider;
import io.orchesty.sdk.authorization.OAuth2StateNonce;
import io.orchesty.sdk.webhook.WebhookManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the application lifecycle (install, settings, limiter keys, custom actions)
 * against the {@link ApplicationInstallRepository}.
 * OAuth authorize/token/refresh is handled by
 * {@link io.orchesty.sdk.authorization.OAuth2Provider} and the application routes.
 */
public class ApplicationManager {

    private static final String AUTHORIZED = "authorized";
    private static final String APPLICATION_SETTINGS = "applicationSettings";
    private static final String CUSTOM_ACTIONS = "customActions";

    private static final String USE_LIMIT = "useLimit";
    private static final String VALUE = "value";
    private static final String TIME = "time";
    private static final String GROUP_VALUE = "groupValue";
    private static final String GROUP_TIME = "groupTime";

    private final Map<String, IApplication> applications;
    private final ApplicationInstallRepository repository;
    private WebhookManager webhookManager;

    public ApplicationManager(Map<String, IApplication> applications, ApplicationInstallRepository repository) {
        this.applications = applications;
        this.repository = repository;
    }

    /**
     * Sets the webhook manager so {@link #detailApplication} can render persisted webhooks.
     *
     * @param webhookManager webhook manager
     * @return this manager
     */
    public ApplicationManager setWebhookManager(WebhookManager webhookManager) {
        this.webhookManager = webhookManager;
        return this;
    }

    public List<Map<String, Object>> getApplications() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (IApplication app : applications.values()) {
            list.add(app.toArray());
        }
        return list;
    }

    public IApplication getApplication(String key) {
        IApplication app = applications.get(key);
        if (app == null) {
            throw new IllegalArgumentException("Application [" + key + "] not found.");
        }
        return app;
    }

    public List<String> getSynchronousActions(String key) {
        return new ArrayList<>(getApplication(key).getSyncMethods());
    }

    /**
     * Runs a synchronous action or lifecycle callback of an application.
     * Backs {@code POST/GET /applications/{name}/sync/{method}}, which the platform calls during
     * install / enable / disable / uninstall, and delegates to
     * {@link IApplication#runSynchronousAction(String, Map)}.
     *
     * @param name application name
     * @param method action or lifecycle callback name
     * @param body request body
     * @return the action result
     */
    public Object runSynchronousAction(String name, String method, Map<String, Object> body) {
        return getApplication(name).runSynchronousAction(method, body);
    }

    /**
     * Persists the frontend redirect URL and returns the provider authorize URL.
     * Backs {@code GET /applications/{name}/users/{user}/sdk/{sdk}/authorize?redirect_url=}.
     *
     * @param name application name
     * @param user user id
     * @param sdk SDK name
     * @param redirectUrl frontend URL to redirect to after authorization
     * @return the provider authorize URL
     */
    public String authorizationApplication(String name, String user, String sdk, String redirectUrl) {
        IOAuth2Application app = asOAuth2(name);
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);
        app.setFrontendRedirectUrl(install, redirectUrl);
        String authorizeUrl = app.authorize(install);
        repository.update(install);
        return authorizeUrl;
    }

    /**
     * Handles an OAuth2 callback (global or per-install): resolves the install from the decoded {@code state},
     * consumes its one-time nonce, then exchanges the callback parameters for a token.
     * The nonce is removed and persisted before the exchange, so a state works only once even
     * when the exchange fails.
     *
     * @param state decoded callback state
     * @param requestParams OAuth2 callback query parameters
     * @return the stored frontend redirect URL
     */
    public String saveAuthorizationToken(OAuth2Provider.State state, Map<String, String> requestParams) {
        IOAuth2Application app = asOAuth2(state.name());
        ApplicationInstall install = loadApplicationInstall(state.name(), state.user(), state.sdk());
        OAuth2StateNonce.consume(install, state.nonce());
        repository.update(install);
        app.setAuthorizationToken(install, requestParams);
        repository.update(install);
        return app.getFrontendRedirectUrl(install);
    }

    /**
     * Computes the limiter keys for the given user's enabled applications.
     * Uses the per-install limiter form values, falling back to the application's
     * {@code getGlobalLimits}, formatted as limiter-key strings.
     *
     * @param user user id
     * @param sdk SDK name
     * @param applications application names
     * @return the limiter keys
     */
    public List<String> userApplicationsLimit(String user, String sdk, List<String> applications) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("users", List.of(user));
        filter.put("names", applications);
        filter.put("sdks", List.of(sdk));
        filter.put("enabled", Boolean.TRUE);
        List<ApplicationInstall> installs = repository.findMany(filter);

        List<String> limits = new ArrayList<>();
        for (ApplicationInstall install : installs) {
            Map<String, Object> limiterForm = asMap(install.getSettings().get(CoreForm.LIMITER_FORM));

            Object useLimit = limiterForm == null ? null : limiterForm.get(USE_LIMIT);
            Object time = limiterForm == null ? null : limiterForm.get(TIME);
            Object value = limiterForm == null ? null : limiterForm.get(VALUE);

            if (isBlank(useLimit) || isBlank(time) || isBlank(value)) {
                IApplication app = applications(install.getName());
                Map<String, Object> global =
                        app instanceof AApplication a ? a.getGlobalLimits(install) : new LinkedHashMap<>();
                useLimit = global.get(USE_LIMIT);
                time = global.get(TIME);
                value = global.get(VALUE);
            }

            if (isBlank(useLimit) || isBlank(time) || isBlank(value)) {
                continue;
            }

            String key = install.getUser() + "|" + sdk + ":" + install.getName();
            Object groupTime = limiterForm == null ? null : limiterForm.get(GROUP_TIME);
            Object groupValue = limiterForm == null ? null : limiterForm.get(GROUP_VALUE);

            if (!isBlank(groupTime) && !isBlank(groupValue)) {
                limits.add(getLimiterKeyWithGroup(
                        key, time, value, "|" + sdk + ":" + install.getName(), groupTime, groupValue));
            } else {
                limits.add(getLimiterKey(key, time, value));
            }
        }
        return limits;
    }

    private IOAuth2Application asOAuth2(String name) {
        IApplication app = getApplication(name);
        if (!(app instanceof IOAuth2Application oauth)) {
            throw new IllegalArgumentException("Application [" + name + "] is not an OAuth2 application.");
        }
        return oauth;
    }

    private IApplication applications(String name) {
        return applications.get(name);
    }

    private static boolean isBlank(Object v) {
        return v == null || String.valueOf(v).isEmpty() || Boolean.FALSE.equals(v);
    }

    private static String decorateLimitKey(String key) {
        return key.contains("|") ? key : key + "|";
    }

    private static String getLimiterKey(String key, Object time, Object amount) {
        return decorateLimitKey(key) + ";" + time + ";" + amount;
    }

    private static String getLimiterKeyWithGroup(
            String key, Object time, Object amount, String groupKey, Object groupTime, Object groupAmount) {
        return decorateLimitKey(key) + ";" + time + ";" + amount + ";" + decorateLimitKey(groupKey) + ";" + groupTime
                + ";" + groupAmount;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    public Map<String, Object> installApplication(String name, String user, String sdk) {
        ApplicationInstall existing = null;
        try {
            existing = repository.findByNameAndUser(name, user, List.of(sdk));
        } catch (RuntimeException ignored) {
            // not found
        }
        if (existing != null) {
            throw new IllegalStateException(
                    "ApplicationInstall with user [" + user + "] and name [" + name + "] already exists !");
        }
        ApplicationInstall install =
                new ApplicationInstall().setUser(user).setName(name).setSdk(sdk);
        repository.insert(install);

        IApplication app = getApplication(install.getName());
        Map<String, Object> out = new LinkedHashMap<>(app.toArray());
        out.put(AUTHORIZED, app.isAuthorized(install));
        out.put(APPLICATION_SETTINGS, app.getApplicationForms(install));
        return out;
    }

    public Map<String, Object> saveApplicationSettings(String name, String user, String sdk, Map<String, Object> data) {
        IApplication app = getApplication(name);
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);

        ApplicationInstall saved = app.saveApplicationForms(install, data);
        repository.update(saved);

        Map<String, Object> out = new LinkedHashMap<>(saved.toArray());
        out.put(APPLICATION_SETTINGS, app.getApplicationForms(saved));
        return out;
    }

    public Map<String, Object> saveApplicationPassword(
            String name, String user, String sdk, String formKey, String fieldKey, String password) {
        IApplication app = getApplication(name);
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);

        ApplicationInstall saved = app.savePassword(install, formKey, fieldKey, password);
        repository.update(saved);

        Map<String, Object> out = new LinkedHashMap<>(saved.toArray());
        out.put(APPLICATION_SETTINGS, app.getApplicationForms(saved));
        return out;
    }

    public void changeStateOfApplication(String name, String user, String sdk, boolean enabled) {
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);
        install.setEnabled(enabled);
        repository.update(install);
    }

    public void uninstallApplication(String name, String user, String sdk) {
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);
        repository.remove(install);
    }

    public Map<String, Object> detailApplication(String name, String user, String sdk) {
        ApplicationInstall install = loadApplicationInstall(name, user, sdk);
        IApplication app = getApplication(install.getName());

        Map<String, Object> out = new LinkedHashMap<>(app.toArray());
        out.put(AUTHORIZED, app.isAuthorized(install));
        out.put("enabled", install.isEnabled());
        out.put(APPLICATION_SETTINGS, app.getApplicationForms(install));
        out.put("webhookSettings", new ArrayList<>());
        out.put(CUSTOM_ACTIONS, customActions(app));
        if (ApplicationType.isWebhook(app.getApplicationType())
                && webhookManager != null
                && app instanceof AApplication aApp) {
            out.put("webhookSettings", webhookManager.getWebhooks(aApp, user, sdk));
        }
        return out;
    }

    public Map<String, Object> userApplications(String user, String sdk) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("users", List.of(user));
        filter.put("sdks", List.of(sdk));
        filter.put("enabled", null);
        List<ApplicationInstall> installs = repository.findMany(filter);

        List<Map<String, Object>> items = new ArrayList<>();
        for (ApplicationInstall install : installs) {
            IApplication app = applications.get(install.getName());
            Map<String, Object> item = new LinkedHashMap<>(install.toArray());
            item.put(AUTHORIZED, app != null && app.isAuthorized(install));
            items.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        return out;
    }

    private static List<Map<String, Object>> customActions(IApplication app) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (app instanceof AApplication aApp) {
            for (CustomAction action : aApp.getCustomActions()) {
                if (action != null) {
                    out.add(action.toArray());
                }
            }
        }
        return out;
    }

    private ApplicationInstall loadApplicationInstall(String name, String user, String sdk) {
        ApplicationInstall install = repository.findByNameAndUser(name, user, List.of(sdk), null);
        if (install == null) {
            throw new IllegalStateException(
                    "ApplicationInstall with user [" + user + "] and name [" + name + "] has not been found!");
        }
        return install;
    }
}
