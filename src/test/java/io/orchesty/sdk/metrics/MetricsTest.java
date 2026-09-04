package io.orchesty.sdk.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.Javalin;
import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.process.Json;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Metrics} against a stub that mimics the worker-api metrics validation.
 * {@code fields.response_error} must be a non-empty string when present, and connectors metrics
 * require the {@code user_id} and {@code node_id} tags.
 */
class MetricsTest {

    private static final Metrics.Times TIMES = new Metrics.Times(12, 1, 1);

    private record Received(String measurement, Map<String, Object> body) {}

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private Javalin stub;
    private Metrics metrics;

    @BeforeEach
    void setUp() {
        stub = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.routes.post("/metrics/{measurement}", ctx -> {
                Map<String, Object> body = Json.fromString(ctx.body(), new TypeReference<>() {});
                received.add(new Received(ctx.pathParam("measurement"), body));
                ctx.status(isValid(ctx.pathParam("measurement"), body) ? 200 : 400)
                        .result("{}");
            });
        });
        stub.start(0);
        metrics = new Metrics(new WorkerApiClient("http://127.0.0.1:" + stub.port(), ""));
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void processMetricsQueueIsBoundedAndDropsOverflow() {
        ThreadPoolExecutor executor = new MetricsHandler(metrics).executor();

        assertEquals(MetricsHandler.QUEUE_CAPACITY, executor.getQueue().remainingCapacity());
        assertInstanceOf(ThreadPoolExecutor.DiscardPolicy.class, executor.getRejectedExecutionHandler());
    }

    @Test
    void curlMetricsWithoutErrorOmitResponseError() {
        assertTrue(sendCurl(200, null));

        Map<String, Object> fields = fields(onlyReceived());
        assertFalse(fields.containsKey("response_error"), fields.toString());
        assertEquals(200, fields.get("response_code"));
    }

    @Test
    void curlMetricsWithBlankErrorOmitResponseError() {
        assertTrue(sendCurl(500, ""));
        assertTrue(sendCurl(500, "  "));

        received.forEach(r -> assertFalse(fields(r).containsKey("response_error"), r.toString()));
    }

    @Test
    void curlMetricsWithErrorSendResponseError() {
        assertTrue(sendCurl(500, "boom"));

        assertEquals("boom", fields(onlyReceived()).get("response_error"));
    }

    @Test
    void curlMetricsCarryAllTags() {
        assertTrue(sendCurl(201, null));

        assertEquals(
                Map.of(
                        "user_id", "orchesty",
                        "node_id", "node-1",
                        "node_name", "abra-flexi-create-product",
                        "topology_id", "topo-1",
                        "application_id", "abra-flexi",
                        "correlation_id", "corr-1",
                        "url", "http://fake-app:8080/c/java/cenik.json"),
                tags(onlyReceived()));
    }

    @Test
    void curlMetricsWithoutUserOrNodeAreNotSent() {
        assertFalse(metrics.sendCurlMetrics(TIMES, 200, null, "node-1", null, null, null, null, "http://x", null));
        assertFalse(metrics.sendCurlMetrics(TIMES, 200, "orchesty", "", null, null, null, null, "http://x", null));

        assertTrue(received.isEmpty(), received.toString());
    }

    @Test
    void rejectedMetricsReturnFalse() {
        stub.stop();
        stub = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.routes.post(
                    "/metrics/{measurement}", ctx -> ctx.status(400).result("{\"message\":{\"error\":\"nope\"}}"));
        });
        stub.start(0);
        metrics = new Metrics(new WorkerApiClient("http://127.0.0.1:" + stub.port(), ""));

        assertFalse(sendCurl(200, null));
    }

    @Test
    void processMetricsCarryTags() {
        assertTrue(metrics.sendProcessMetrics(TIMES, "topo-1", "node-1", "corr-1"));

        Received r = onlyReceived();
        assertEquals("monolith", r.measurement());
        assertEquals(Map.of("topology_id", "topo-1", "node_id", "node-1", "correlation_id", "corr-1"), tags(r));
    }

    private boolean sendCurl(int status, String error) {
        return metrics.sendCurlMetrics(
                TIMES,
                status,
                "orchesty",
                "node-1",
                "abra-flexi-create-product",
                "topo-1",
                "abra-flexi",
                "corr-1",
                "http://fake-app:8080/c/java/cenik.json",
                error);
    }

    private Received onlyReceived() {
        assertEquals(1, received.size(), received.toString());
        return received.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fields(Received r) {
        return (Map<String, Object>) r.body().get("fields");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tags(Received r) {
        return (Map<String, Object>) r.body().get("tags");
    }

    @SuppressWarnings("unchecked")
    private static boolean isValid(String measurement, Map<String, Object> body) {
        Map<String, Object> fields = (Map<String, Object>) body.get("fields");
        Map<String, Object> tags = (Map<String, Object>) body.get("tags");
        if (fields == null || tags == null) {
            return false;
        }
        if (fields.containsKey("response_error")
                && !(fields.get("response_error") instanceof String s && !s.isEmpty())) {
            return false;
        }
        return !"connectors".equals(measurement) || (tags.get("user_id") != null && tags.get("node_id") != null);
    }
}
