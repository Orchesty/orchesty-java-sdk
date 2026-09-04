package io.orchesty.sdk.metrics;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.orchesty.sdk.http.Responses;
import io.orchesty.sdk.process.Headers;
import io.orchesty.sdk.process.Json;
import io.orchesty.sdk.process.MessageDto;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Javalin before/after middleware that records per-request process metrics.
 * Captures a start snapshot on {@code before} and, after the response, sends the
 * elapsed times to the {@code monolith} measurement.
 * The send runs off the request thread so it never adds latency to the response. At most
 * {@value #QUEUE_CAPACITY} sends wait in the queue; further metrics are dropped while
 * worker-api is slow, so they cannot exhaust memory.
 */
public class MetricsHandler {

    static final int QUEUE_CAPACITY = 1000;

    private static final String ATTR = "orchesty.startMetrics";

    private final Metrics metrics;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "orchesty-metrics");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    public MetricsHandler(Metrics metrics) {
        this.metrics = metrics;
    }

    public void register(JavalinConfig cfg) {
        cfg.routes.before(ctx -> ctx.attribute(ATTR, Metrics.getCurrentMetrics()));
        cfg.routes.after(this::afterResponse);
    }

    ThreadPoolExecutor executor() {
        return executor;
    }

    private void afterResponse(Context ctx) {
        Metrics.StartMetrics start = ctx.attribute(ATTR);
        if (start == null) {
            return;
        }
        Metrics.Times times = Metrics.getTimes(start);
        Map<String, Object> headers = extractHeaders(ctx);
        String topologyId = str(headers.get(Headers.TOPOLOGY_ID));
        String nodeId = str(headers.get(Headers.NODE_ID));
        String correlationId = str(headers.get(Headers.CORRELATION_ID));
        executor.execute(() -> metrics.sendProcessMetrics(times, topologyId, nodeId, correlationId));
    }

    /**
     * Returns the envelope headers: those already parsed by the process routes, otherwise
     * parsed from the body (e.g. when the route failed before parsing).
     */
    private static Map<String, Object> extractHeaders(Context ctx) {
        Map<String, Object> parsed = ctx.attribute(Responses.REQUEST_HEADERS_ATTR);
        if (parsed != null) {
            return parsed;
        }
        try {
            String body = ctx.body();
            if (body == null || body.isEmpty()) {
                return Map.of();
            }
            MessageDto msg = Json.fromString(body, MessageDto.class);
            return msg.getHeaders() == null ? Map.of() : msg.getHeaders();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
