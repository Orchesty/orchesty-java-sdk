package io.orchesty.sdk.http;

import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.orchesty.sdk.middleware.ErrorHandler;
import io.orchesty.sdk.node.IBatch;
import io.orchesty.sdk.node.IConnector;
import io.orchesty.sdk.node.ICustomNode;
import io.orchesty.sdk.node.NodeRegistry;
import io.orchesty.sdk.process.BatchProcessDto;
import io.orchesty.sdk.process.ProcessDto;
import java.util.List;

/**
 * Registers the worker HTTP routes on a Javalin config.
 * Javalin 7 adds routes before {@code create()} returns. The custom-node, connector,
 * batch and status routes follow the Orchesty worker contract expected by the Bridge
 * and ServiceLocator.
 */
public final class WorkerRoutes {

    private WorkerRoutes() {}

    public static void register(JavalinConfig cfg, NodeRegistry registry) {
        register(cfg, registry, new ErrorHandler(null));
    }

    public static void register(JavalinConfig cfg, NodeRegistry registry, ErrorHandler errorHandler) {
        registerCustomNodeRoutes(cfg.routes, registry, errorHandler);
        registerConnectorRoutes(cfg.routes, registry, errorHandler);
        registerBatchRoutes(cfg.routes, registry, errorHandler);

        cfg.routes.get("/status", ctx -> ctx.contentType("application/json").result("{\"status\":true}"));
    }

    private static void registerCustomNodeRoutes(RoutesConfig app, NodeRegistry registry, ErrorHandler errorHandler) {
        app.post("/custom-node/{name}/process", ctx -> {
            ProcessDto dto = new ProcessDto();
            ICustomNode node = null;
            try {
                dto = Responses.parseRequest(ctx, "");
                node = registry.getCustomNode(ctx.pathParam("name"));
                ProcessDto result = node.process(dto);
                Responses.applyAuditCheckpoint(result, node);
                Responses.success(ctx, result);
            } catch (Exception e) {
                Responses.applyAuditCheckpoint(dto, node);
                errorHandler.handle(ctx, dto, e);
            }
        });

        app.get("/custom-node/{name}/process/test", ctx -> {
            try {
                registry.getCustomNode(ctx.pathParam("name"));
                ctx.contentType("application/json").result("[]");
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.get("/custom-node/list", ctx -> {
            try {
                List<NodeRegistry.ListItem> list = registry.listCustomNodes();
                ctx.contentType("application/json").result(io.orchesty.sdk.process.Json.toString(list));
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });
    }

    private static void registerConnectorRoutes(RoutesConfig app, NodeRegistry registry, ErrorHandler errorHandler) {
        app.post("/connector/{name}/action", ctx -> {
            IConnector connector = null;
            String appName = "";
            try {
                connector = registry.getConnector(ctx.pathParam("name"));
                appName = connector.getApplicationName();
            } catch (Exception ignored) {
                // resolved below; keep appName empty for parse
            }
            ProcessDto dto = new ProcessDto();
            try {
                dto = Responses.parseRequest(ctx, appName);
                if (connector == null) {
                    connector = registry.getConnector(ctx.pathParam("name"));
                }
                ProcessDto result = connector.process(dto);
                Responses.applyAuditCheckpoint(result, connector);
                Responses.success(ctx, result);
            } catch (Exception e) {
                Responses.applyAuditCheckpoint(dto, connector);
                errorHandler.handle(ctx, dto, e);
            }
        });

        app.get("/connector/{name}/action/test", ctx -> {
            try {
                registry.getConnector(ctx.pathParam("name"));
                ctx.contentType("application/json").result("[]");
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.get("/connector/list", ctx -> {
            try {
                List<NodeRegistry.ListItem> list = registry.listConnectors();
                ctx.contentType("application/json").result(io.orchesty.sdk.process.Json.toString(list));
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });
    }

    private static void registerBatchRoutes(RoutesConfig app, NodeRegistry registry, ErrorHandler errorHandler) {
        app.post("/batch/{name}/action", ctx -> {
            IBatch batch = null;
            String appName = "";
            try {
                batch = registry.getBatch(ctx.pathParam("name"));
                appName = batch.getApplicationName();
            } catch (Exception ignored) {
                // resolved below; keep appName empty for parse
            }
            BatchProcessDto dto = new BatchProcessDto();
            try {
                dto = Responses.parseBatchRequest(ctx, appName);
                if (batch == null) {
                    batch = registry.getBatch(ctx.pathParam("name"));
                }
                // getBridgeData() is polymorphic: a BatchProcessDto serializes to the
                // JSON message array the Bridge's batch worker expects.
                BatchProcessDto result = batch.process(dto);
                Responses.applyAuditCheckpoint(result, batch);
                Responses.success(ctx, result);
            } catch (Exception e) {
                Responses.applyAuditCheckpoint(dto, batch);
                errorHandler.handle(ctx, dto, e);
            }
        });

        app.get("/batch/{name}/action/test", ctx -> {
            try {
                registry.getBatch(ctx.pathParam("name"));
                ctx.contentType("application/json").result("[]");
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });

        app.get("/batch/list", ctx -> {
            try {
                List<NodeRegistry.ListItem> list = registry.listBatches();
                ctx.contentType("application/json").result(io.orchesty.sdk.process.Json.toString(list));
            } catch (Exception e) {
                Responses.apiError(ctx, e);
            }
        });
    }
}
