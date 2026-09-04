package io.orchesty.sdk.http;

import io.javalin.http.Context;
import io.orchesty.sdk.process.BatchProcessDto;
import io.orchesty.sdk.process.Headers;
import io.orchesty.sdk.process.Json;
import io.orchesty.sdk.process.MessageDto;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.process.ResultCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds worker HTTP responses in the exact shape the Bridge expects.
 * Provides success, business-error and API-error responses, plus parsing of the
 * incoming {@link MessageDto} envelope.
 */
public final class Responses {

    private static final Logger LOG = LoggerFactory.getLogger(Responses.class);
    private static final String JSON = "application/json";

    /**
     * Request attribute holding the envelope headers as received, set by {@link #parseRequest}
     * and {@link #parseBatchRequest} so later handlers need not parse the body again.
     */
    public static final String REQUEST_HEADERS_ATTR = "orchesty.requestHeaders";

    private Responses() {}

    /**
     * Parses the {@link MessageDto} envelope from the request body into a {@link ProcessDto}.
     *
     * @param ctx request context
     * @param appName name of the application bound to the target node, or empty
     * @return the parsed process DTO
     */
    public static ProcessDto parseRequest(Context ctx, String appName) {
        MessageDto msg = parseEnvelope(ctx);

        ProcessDto dto = new ProcessDto();
        String body = msg.getBody();
        dto.setData(body == null || body.isEmpty() ? "{}" : body);
        dto.setHeaders(normalizeHeaders(msg.getHeaders()));
        dto.setCurrentApp(appName);
        return dto;
    }

    /**
     * Parses the {@link MessageDto} envelope into a {@link BatchProcessDto}.
     * The batch's trigger payload lands in {@link BatchProcessDto#getJsonData()} and its
     * (optional) cursor in {@link BatchProcessDto#getBatchCursor()}; output is the emitted
     * messages.
     *
     * @param ctx request context
     * @param appName name of the application bound to the target batch, or empty
     * @return the parsed batch process DTO
     */
    public static BatchProcessDto parseBatchRequest(Context ctx, String appName) {
        MessageDto msg = parseEnvelope(ctx);

        BatchProcessDto dto = new BatchProcessDto();
        String body = msg.getBody();
        dto.setData(body == null || body.isEmpty() ? "{}" : body);
        dto.setHeaders(normalizeHeaders(msg.getHeaders()));
        dto.setCurrentApp(appName);
        return dto;
    }

    private static MessageDto parseEnvelope(Context ctx) {
        String raw = ctx.body();
        MessageDto msg = (raw == null || raw.isEmpty()) ? new MessageDto() : Json.fromString(raw, MessageDto.class);
        // A copy: nodes may change the DTO headers, metrics need the received ones.
        ctx.attribute(
                REQUEST_HEADERS_ATTR, msg.getHeaders() == null ? Map.of() : new LinkedHashMap<>(msg.getHeaders()));
        return msg;
    }

    /**
     * Adds the {@code audit-checkpoint} header when {@code node} declares a business audit
     * checkpoint, so the Bridge can emit a structured audit log.
     * No-op unless {@code node} implements
     * {@link io.orchesty.sdk.node.IAuditCheckpointProvider} and returns a checkpoint.
     *
     * @param dto response DTO to add the header to
     * @param node node that produced the response
     */
    public static void applyAuditCheckpoint(ProcessDto dto, Object node) {
        if (node instanceof io.orchesty.sdk.node.IAuditCheckpointProvider provider) {
            io.orchesty.sdk.node.IAuditCheckpoint checkpoint = provider.getAuditCheckpoint();
            if (checkpoint != null) {
                dto.addHeader(Headers.AUDIT_CHECKPOINT, checkpoint.toJson());
            }
        }
    }

    public static void success(Context ctx, ProcessDto dto) {
        if (!dto.hasHeader(Headers.RESULT_CODE)) {
            dto.addHeader(Headers.RESULT_CODE, Integer.toString(ResultCode.SUCCESS.code()));
        }
        if (!dto.hasHeader(Headers.RESULT_MESSAGE)) {
            dto.addHeader(Headers.RESULT_MESSAGE, "Processed successfully.");
        }
        ctx.status(200).contentType(JSON).result(envelope(dto));
    }

    public static void error(Context ctx, ProcessDto dto, Throwable e) {
        LOG.warn("[worker] Business error on {} {}: {}", ctx.method(), ctx.path(), safeMessage(e), e);
        if (!dto.hasHeader(Headers.RESULT_CODE)) {
            dto.addHeader(Headers.RESULT_CODE, Integer.toString(ResultCode.STOP_AND_FAILED.code()));
        }
        String msg = ("Error occurred: " + safeMessage(e)).replaceAll("\\r?\\n|\\r", "");
        if (!dto.hasHeader(Headers.RESULT_MESSAGE)) {
            dto.addHeader(Headers.RESULT_MESSAGE, msg);
        } else {
            dto.addHeader(
                    Headers.RESULT_MESSAGE,
                    "Error: " + msg + ", Original result: " + dto.getHeader(Headers.RESULT_MESSAGE));
        }
        ctx.status(400).contentType(JSON).result(envelope(dto));
    }

    /**
     * Writes a framework-level error response (node not found, bad request), not a business error.
     *
     * @param ctx request context
     * @param e error to report
     */
    public static void apiError(Context ctx, Throwable e) {
        LOG.warn("[worker] API error on {} {}: {}", ctx.method(), ctx.path(), safeMessage(e), e);
        String detail = safeMessage(e);
        Map<String, Object> body = new LinkedHashMap<>();
        if (detail != null && !detail.isEmpty()) {
            ctx.status(400);
            body.put("status", "Error");
            body.put("message", "Error occurred: " + detail);
        } else {
            ctx.status(500);
            body.put("status", "Error");
            body.put("message", "Error occurred: unknown reason");
        }
        ctx.contentType(JSON).result(Json.toString(body));
    }

    private static String envelope(ProcessDto dto) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("body", dto.getBridgeData());
        out.put("headers", dto.getHeaders());
        return Json.toString(out);
    }

    private static Map<String, String> normalizeHeaders(Map<String, Object> raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (raw == null) {
            return headers;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(String.valueOf(list.get(i)));
                }
                headers.put(e.getKey(), sb.toString());
            } else {
                headers.put(e.getKey(), String.valueOf(v));
            }
        }
        return headers;
    }

    private static String safeMessage(Throwable e) {
        return e == null ? null : (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
