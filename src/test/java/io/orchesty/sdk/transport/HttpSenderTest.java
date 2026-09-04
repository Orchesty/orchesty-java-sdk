package io.orchesty.sdk.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.exception.OnRepeatException;
import io.orchesty.sdk.exception.OnStopAndFailException;
import io.orchesty.sdk.process.ProcessDto;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The SDK's HTTP clients must speak plain HTTP/1.1 (no h2c upgrade), and {@link HttpSender} repeats
 * only network errors.
 */
class HttpSenderTest {

    private final Map<String, String> headers = new ConcurrentHashMap<>();
    private Javalin stub;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        stub = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.routes.get("/ping", ctx -> {
                ctx.headerMap().forEach((k, v) -> headers.put(k.toLowerCase(Locale.ROOT), v));
                ctx.result("{\"ok\":true}");
            });
        });
        stub.start(0);
        baseUrl = "http://127.0.0.1:" + stub.port();
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void httpSenderDoesNotRequestH2cUpgrade() {
        ResponseDto response =
                new HttpSender().send(new RequestDto(baseUrl + "/ping", HttpMethods.GET, new ProcessDto()));

        assertEquals(200, response.getResponseCode());
        assertNoUpgrade();
    }

    @Test
    void workerApiClientDoesNotRequestH2cUpgrade() {
        WorkerApiClient.Response response = new WorkerApiClient(baseUrl, "").send("/ping", "GET", null);

        assertEquals(200, response.status());
        assertNoUpgrade();
    }

    @Test
    void connectionErrorIsRepeated() {
        RequestDto request = new RequestDto("http://127.0.0.1:1/ping", HttpMethods.GET, new ProcessDto());

        assertThrows(OnRepeatException.class, () -> new HttpSender().send(request));
    }

    @Test
    void invalidRequestStopsInsteadOfRepeating() {
        RequestDto request = new RequestDto("http://bad host/ping", HttpMethods.GET, new ProcessDto());

        assertThrows(OnStopAndFailException.class, () -> new HttpSender().send(request));
    }

    @Test
    void interruptStopsAndKeepsTheInterruptFlag() {
        RequestDto request = new RequestDto(baseUrl + "/ping", HttpMethods.GET, new ProcessDto());

        Thread.currentThread().interrupt();
        try {
            assertThrows(OnStopAndFailException.class, () -> new HttpSender().send(request));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void assertNoUpgrade() {
        assertNull(headers.get("upgrade"), headers.toString());
        assertNull(headers.get("http2-settings"), headers.toString());
        assertFalse(
                headers.getOrDefault("connection", "").toLowerCase(Locale.ROOT).contains("upgrade"),
                headers.toString());
    }
}
