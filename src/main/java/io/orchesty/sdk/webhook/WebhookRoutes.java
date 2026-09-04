package io.orchesty.sdk.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.orchesty.sdk.http.Responses;
import io.orchesty.sdk.process.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy webhook routes kept for backward compatibility with the old gateway.
 * Registers {@code POST /webhook/applications/{name}/users/{user}/sdk/{sdk}/(un)subscribe}.
 * New integrations expose webhooks via {@link AWebhookApplication} and let the UI drive the sync actions.
 */
public final class WebhookRoutes {

    private static final String JSON = "application/json";

    private WebhookRoutes() {}

    public static void register(JavalinConfig cfg, WebhookManager manager) {
        RoutesConfig app = cfg.routes;
        app.post("/webhook/applications/{name}/users/{user}/sdk/{sdk}/subscribe", ctx -> {
            try {
                List<Webhook> result = manager.subscribeWebhooks(
                        ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"), parseBody(ctx));
                json(ctx, ids(result));
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.post("/webhook/applications/{name}/users/{user}/sdk/{sdk}/unsubscribe", ctx -> {
            try {
                List<Webhook> result = manager.unsubscribeWebhooks(
                        ctx.pathParam("name"), ctx.pathParam("user"), ctx.pathParam("sdk"), parseBody(ctx));
                json(ctx, ids(result));
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });
    }

    private static List<String> ids(List<Webhook> webhooks) {
        List<String> ids = new ArrayList<>();
        for (Webhook w : webhooks) {
            if (w != null && w.getId() != null && !w.getId().isEmpty()) {
                ids.add(w.getId());
            }
        }
        return ids;
    }

    private static Map<String, Object> parseBody(Context ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return Json.fromString(body, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static void json(Context ctx, Object body) {
        ctx.status(200).contentType(JSON).result(Json.toString(body));
    }
}
