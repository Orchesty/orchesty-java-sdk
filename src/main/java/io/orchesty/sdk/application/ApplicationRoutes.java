package io.orchesty.sdk.application;

import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.orchesty.sdk.authorization.OAuth2Provider;
import io.orchesty.sdk.http.Responses;
import io.orchesty.sdk.process.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the application HTTP endpoints on Javalin so the Orchesty UI can list,
 * install, configure, authorize and uninstall applications.
 */
public final class ApplicationRoutes {

    private static final String JSON = "application/json";

    private ApplicationRoutes() {}

    public static void register(JavalinConfig cfg, ApplicationManager manager) {
        RoutesConfig app = cfg.routes;
        app.get("/applications", ctx -> {
            try {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("items", manager.getApplications());
                json(ctx, 200, out);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.get("/applications/{name}", ctx -> {
            try {
                Map<String, Object> out = new LinkedHashMap<>(
                        manager.getApplication(ctx.pathParam("name")).toArray());
                out.put("syncMethods", manager.getSynchronousActions(ctx.pathParam("name")));
                json(ctx, 200, out);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        // Per-user application limiter keys used by the platform limiter.
        app.post("/applications/sdk/{sdk}/limits", ctx -> {
            try {
                Map<String, Object> body = parseBody(ctx);
                String user = String.valueOf(body.get("user"));
                @SuppressWarnings("unchecked")
                List<String> applications =
                        body.get("applications") instanceof List<?> l ? (List<String>) l : List.of();
                List<String> res = manager.userApplicationsLimit(user, ctx.pathParam("sdk"), applications);
                json(ctx, 200, res);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        // OAuth2: build the provider authorize URL for this install.
        app.get("/applications/{name}/users/{user}/sdk/{sdk}/authorize", ctx -> {
            try {
                String redirectUrl = ctx.queryParam("redirect_url");
                if (redirectUrl == null || redirectUrl.isEmpty()) {
                    Responses.apiError(ctx, new IllegalArgumentException("Missing \"redirect_url\" query parameter."));
                    return;
                }
                String url = manager.authorizationApplication(
                        ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"), redirectUrl);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("authorizeUrl", url);
                json(ctx, 200, out);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        // OAuth2: per-install callback URL. The 'state' must name the same install as the path and
        // carry its one-time nonce, exactly like the global callback below.
        app.get("/applications/{name}/users/{user}/sdk/{sdk}/authorize/token", ctx -> {
            try {
                String state = ctx.queryParam("state");
                if (state == null || state.isEmpty()) {
                    Responses.apiError(ctx, new IllegalArgumentException("Missing \"state\" query parameter."));
                    return;
                }
                OAuth2Provider.State decoded = OAuth2Provider.stateDecode(state);
                if (!decoded.name().equals(ctx.pathParam("name"))
                        || !decoded.user().equals(ctx.pathParam("user"))
                        || !decoded.sdk().equals(ctx.pathParam("sdk"))) {
                    Responses.apiError(ctx, new IllegalStateException("Invalid OAuth2 state."));
                    return;
                }
                String url = manager.saveAuthorizationToken(decoded, queryParams(ctx));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("redirectUrl", url);
                json(ctx, 200, out);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        // OAuth2: global callback URL - the install is resolved from the encoded 'state'.
        app.get("/applications/authorize/token", ctx -> {
            try {
                String state = ctx.queryParam("state");
                if (state == null || state.isEmpty()) {
                    Responses.apiError(ctx, new IllegalArgumentException("Missing \"state\" query parameter."));
                    return;
                }
                String url = manager.saveAuthorizationToken(OAuth2Provider.stateDecode(state), queryParams(ctx));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("redirectUrl", url);
                json(ctx, 200, out);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.get("/applications/{name}/sync/list", ctx -> {
            try {
                json(ctx, 200, manager.getSynchronousActions(ctx.pathParam("name")));
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        // Synchronous actions + lifecycle callbacks (afterInstall/afterEnable/afterDisable/
        // afterUninstall). The platform calls these during install/activate/deactivate and
        // treats a 404 as fatal (→ "Cannot activate application"), so the route must exist.
        Handler syncHandler = ctx -> {
            try {
                Object res =
                        manager.runSynchronousAction(ctx.pathParam("name"), ctx.pathParam("method"), parseBody(ctx));
                json(ctx, 200, res);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        };
        app.get("/applications/{name}/sync/{method}", syncHandler);
        app.post("/applications/{name}/sync/{method}", syncHandler);

        app.post("/applications/{name}/users/{user}/sdk/{sdk}/install", ctx -> {
            try {
                Map<String, Object> res =
                        manager.installApplication(ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"));
                json(ctx, 201, res);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.put("/applications/{name}/users/{user}/sdk/{sdk}/settings", ctx -> {
            try {
                Map<String, Object> data = parseBody(ctx);
                Map<String, Object> res = manager.saveApplicationSettings(
                        ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"), data);
                json(ctx, 200, res);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.put("/applications/{name}/users/{user}/sdk/{sdk}/password", ctx -> {
            try {
                Map<String, Object> body = parseBody(ctx);
                Object password = body.get("password");
                Object formKey = body.get("formKey");
                Object fieldKey = body.get("fieldKey");
                if (password == null || formKey == null || fieldKey == null) {
                    Responses.apiError(
                            ctx,
                            new IllegalArgumentException(
                                    "Missing required parameters [password, formKey, fieldKey] in body."));
                    return;
                }
                Map<String, Object> res = manager.saveApplicationPassword(
                        ctx.pathParam("name"),
                        ctx.pathParam("user"),
                        ctx.pathParam("sdk"),
                        String.valueOf(formKey),
                        String.valueOf(fieldKey),
                        String.valueOf(password));
                json(ctx, 200, res);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.delete("/applications/{name}/users/{user}/sdk/{sdk}/uninstall", ctx -> {
            try {
                manager.uninstallApplication(ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"));
                json(ctx, 200, new LinkedHashMap<>());
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.put("/applications/{name}/users/{user}/sdk/{sdk}/changeState", ctx -> {
            try {
                Map<String, Object> body = parseBody(ctx);
                boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
                manager.changeStateOfApplication(
                        ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"), enabled);
                json(ctx, 200, new LinkedHashMap<>());
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.get("/applications/{name}/users/{user}/sdk/{sdk}", ctx -> {
            try {
                Map<String, Object> res =
                        manager.detailApplication(ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"));
                json(ctx, 200, res);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.get("/applications/users/{user}/sdk/{sdk}", ctx -> {
            try {
                Map<String, Object> res = manager.userApplications(ctx.pathParam("user"), ctx.pathParam("sdk"));
                json(ctx, 200, res);
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });
    }

    private static Map<String, Object> parseBody(Context ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return Json.fromString(body, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    /**
     * Flattens Javalin's multi-valued query parameters to a single-value map (first value wins).
     *
     * @param ctx request context
     * @return query parameters mapped to their first value
     */
    private static Map<String, String> queryParams(Context ctx) {
        Map<String, String> out = new LinkedHashMap<>();
        ctx.queryParamMap().forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                out.put(k, v.get(0));
            }
        });
        return out;
    }

    private static void json(Context ctx, int status, Object body) {
        ctx.status(status).contentType(JSON).result(Json.toString(body));
    }
}
