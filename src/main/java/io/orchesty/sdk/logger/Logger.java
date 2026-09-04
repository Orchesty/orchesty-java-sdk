package io.orchesty.sdk.logger;

import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.process.Headers;
import io.orchesty.sdk.process.Json;
import io.orchesty.sdk.process.ProcessDto;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured logger that emits JSON log lines enriched with the process correlation context.
 * Each line is written to stdout and, when {@code isForUi} is set, also posted to the
 * Worker-API {@code /logger/logs} endpoint so it shows up in the Orchesty UI.
 */
public class Logger {

    private static final org.slf4j.Logger STDOUT = org.slf4j.LoggerFactory.getLogger("orchesty.sdk");

    private final WorkerApiClient client;

    public Logger(WorkerApiClient client) {
        this.client = client;
    }

    public void debug(String message, ProcessDto ctx) {
        debug(message, ctx, false);
    }

    public void debug(String message, ProcessDto ctx, boolean isForUi) {
        emit("debug", message, ctx, null, isForUi);
    }

    public void info(String message, ProcessDto ctx) {
        info(message, ctx, false);
    }

    public void info(String message, ProcessDto ctx, boolean isForUi) {
        emit("info", message, ctx, null, isForUi);
    }

    public void warn(String message, ProcessDto ctx) {
        warn(message, ctx, false);
    }

    public void warn(String message, ProcessDto ctx, boolean isForUi) {
        emit("warn", message, ctx, null, isForUi);
    }

    public void error(String message, ProcessDto ctx) {
        error(message, ctx, false, null);
    }

    public void error(String message, ProcessDto ctx, boolean isForUi, Throwable err) {
        emit("error", message, ctx, err, isForUi);
    }

    private void emit(String level, String message, ProcessDto ctx, Throwable err, boolean isForUi) {
        Map<String, Object> line = createCtx(ctx);
        line.put("timestamp", System.currentTimeMillis());
        line.put("service", "sdk");
        line.put("levelName", level);
        line.put("message", message == null ? null : message.replaceAll("\\s\\s+", " "));
        if (err != null) {
            Map<String, Object> stack = new LinkedHashMap<>();
            stack.put(
                    "message", err.getMessage() == null ? "" : err.getMessage().replaceAll("\\s\\s+", " "));
            stack.put("trace", stackToString(err));
            line.put("stacktrace", stack);
        }

        String json = Json.toString(line);
        switch (level) {
            case "debug" -> STDOUT.debug(json);
            case "warn" -> STDOUT.warn(json);
            case "error" -> STDOUT.error(json);
            default -> STDOUT.info(json);
        }

        if (isForUi) {
            send(line);
        }
    }

    /**
     * Builds the log context (correlation IDs, node/topology, user, ...) from process headers.
     *
     * @param ctx process whose headers are read; may be {@code null}
     * @return the log context map, empty when {@code ctx} is {@code null}
     */
    public Map<String, Object> createCtx(ProcessDto ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (ctx == null) {
            return out;
        }
        putHeader(out, ctx, "previousNodeId", Headers.PREV_NODE_ID);
        putHeader(out, ctx, "nodeId", Headers.NODE_ID);
        putHeader(out, ctx, "nodeName", Headers.NODE_NAME);
        putHeader(out, ctx, "previousCorrelationId", Headers.PREV_CORRELATION_ID);
        putHeader(out, ctx, "correlationId", Headers.CORRELATION_ID);
        putHeader(out, ctx, "topologyId", Headers.TOPOLOGY_ID);
        putHeader(out, ctx, "topologyName", Headers.TOPOLOGY_NAME);
        putHeader(out, ctx, "processId", Headers.PROCESS_ID);
        putHeader(out, ctx, "parentId", Headers.PARENT_ID);
        putHeader(out, ctx, "sequenceId", Headers.SEQUENCE_ID);
        putHeader(out, ctx, "userId", Headers.USER);
        putHeader(out, ctx, "applications", Headers.APPLICATIONS);
        return out;
    }

    private void send(Map<String, Object> line) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(line);
            body.put("isForUi", true);
            client.send("/logger/logs", "POST", body);
        } catch (RuntimeException e) {
            STDOUT.debug("[logger] failed to post UI log: {}", e.getMessage());
        }
    }

    private static void putHeader(Map<String, Object> out, ProcessDto ctx, String key, String header) {
        String value = ctx.getHeader(header);
        if (value != null && !value.isEmpty()) {
            out.put(key, value);
        }
    }

    private static String stackToString(Throwable err) {
        java.io.StringWriter sw = new java.io.StringWriter();
        err.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
