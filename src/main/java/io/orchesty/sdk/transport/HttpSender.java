package io.orchesty.sdk.transport;

import io.orchesty.sdk.exception.OnRepeatException;
import io.orchesty.sdk.exception.OnStopAndFailException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native HTTP client for hand-written connectors and applications (no Apache Camel).
 * Executes a single request and translates the HTTP status into success / repeat /
 * stop-and-fail via {@link ResultRanges}. The SDK never retries in-process; a
 * retryable outcome is signalled by throwing {@link OnRepeatException}.
 */
public class HttpSender {

    private static final Logger LOG = LoggerFactory.getLogger(HttpSender.class);
    private static final int DEFAULT_INTERVAL_SEC = 60;
    private static final int DEFAULT_MAX_HOPS = 10;

    private final HttpClient client;
    private CurlMetricsRecorder metricsRecorder;

    /**
     * Creates a sender backed by an HTTP/1.1 client with a 30-second connect timeout.
     * HTTP/1.1 is forced because the JDK default HTTP/2 sends an h2c upgrade on plain http.
     */
    public HttpSender() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    public HttpSender(HttpClient client) {
        this.client = client;
    }

    /**
     * Sets the optional metrics hook invoked after every call.
     *
     * @param recorder metrics recorder to use
     * @return this sender
     */
    public HttpSender setMetricsRecorder(CurlMetricsRecorder recorder) {
        this.metricsRecorder = recorder;
        return this;
    }

    public ResponseDto send(RequestDto dto) {
        return send(dto, ResultRanges.DEFAULT, DEFAULT_INTERVAL_SEC, DEFAULT_MAX_HOPS);
    }

    public ResponseDto send(RequestDto dto, ResultRanges ranges) {
        return send(dto, ranges, DEFAULT_INTERVAL_SEC, DEFAULT_MAX_HOPS);
    }

    public ResponseDto send(RequestDto dto, ResultRanges ranges, int sec, int hops) {
        ResultRanges effective = ranges == null ? ResultRanges.DEFAULT : ranges;
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = buildRequest(dto);
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            long duration = System.currentTimeMillis() - start;

            int status = response.statusCode();
            String body = new String(response.body() == null ? new byte[0] : response.body(), StandardCharsets.UTF_8);
            ResponseDto responseDto = new ResponseDto(body, status, null, flattenHeaders(response));

            recordMetrics(dto, status, duration, status >= 300 ? body : null);
            log(dto, status, body);

            return handleByResultCode(dto, responseDto, effective, sec, hops);
        } catch (HttpTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            recordMetrics(dto, 408, duration, e.getMessage());
            throw new OnRepeatException(sec, hops, "Request timed out: " + dto.getMethod() + " " + dto.getUrl(), e);
        } catch (OnRepeatException | OnStopAndFailException e) {
            throw e;
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - start;
            recordMetrics(dto, 500, duration, e.getMessage());
            // Connection refused / IO errors are transient - let the Bridge retry.
            throw new OnRepeatException(sec, hops, failure(dto, e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordMetrics(dto, 500, System.currentTimeMillis() - start, "interrupted");
            throw new OnStopAndFailException(failure(dto, e), e);
        } catch (RuntimeException e) {
            // Invalid URI, restricted headers, serialization errors: retrying would never succeed.
            recordMetrics(dto, 500, System.currentTimeMillis() - start, e.getMessage());
            throw new OnStopAndFailException(failure(dto, e), e);
        }
    }

    private static String failure(RequestDto dto, Exception e) {
        return "Request failed: " + dto.getMethod() + " " + dto.getUrl() + " - "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private ResponseDto handleByResultCode(
            RequestDto dto, ResponseDto response, ResultRanges ranges, int sec, int hops) {
        int status = response.getResponseCode();
        if (StatusRanges.inRange(status, ranges.getSuccess())) {
            return response;
        }
        if (StatusRanges.inRange(status, ranges.getStopAndFail())) {
            throw new OnStopAndFailException(
                    "Non-retryable status " + status + " from " + dto.getUrl() + ": " + response.getBody());
        }
        if (StatusRanges.inRange(status, ranges.getRepeat())) {
            throw new OnRepeatException(
                    sec, hops, "Retryable status " + status + " from " + dto.getUrl() + ": " + response.getBody());
        }
        // Fallback: <300 success, otherwise repeat.
        if (status < 300) {
            return response;
        }
        throw new OnRepeatException(
                sec, hops, "Unclassified status " + status + " from " + dto.getUrl() + ": " + response.getBody());
    }

    private HttpRequest buildRequest(RequestDto dto) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder().uri(URI.create(dto.getUrl())).timeout(Duration.ofMillis(dto.getTimeout()));

        HttpRequest.BodyPublisher publisher = bodyPublisher(dto.getBody());
        builder.method(dto.getMethod().name(), publisher);

        for (Map.Entry<String, String> h : dto.getHeaders().entrySet()) {
            if (h.getValue() != null) {
                builder.header(h.getKey(), h.getValue());
            }
        }

        RequestDto.Auth auth = dto.getAuth();
        if (auth != null) {
            String token = Base64.getEncoder()
                    .encodeToString((auth.username() + ":" + auth.password()).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }

        return builder.build();
    }

    private static HttpRequest.BodyPublisher bodyPublisher(Object body) {
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        String data = body instanceof String s ? s : io.orchesty.sdk.process.Json.toString(body);
        return HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8);
    }

    private static Map<String, String> flattenHeaders(HttpResponse<?> response) {
        Map<String, String> out = new LinkedHashMap<>();
        response.headers().map().forEach((k, v) -> out.put(k, String.join(",", v)));
        return out;
    }

    private void recordMetrics(RequestDto dto, int status, long durationMillis, String error) {
        if (metricsRecorder != null) {
            try {
                metricsRecorder.record(dto, status, durationMillis, error);
            } catch (Exception e) {
                LOG.debug("[transport] metrics recording failed: {}", e.getMessage());
            }
        }
    }

    private static void log(RequestDto dto, int status, String body) {
        if (status < 300) {
            LOG.info("[transport] {} {} -> {}", dto.getMethod(), dto.getUrl(), status);
        } else {
            LOG.warn("[transport] {} {} -> {} body={}", dto.getMethod(), dto.getUrl(), status, body);
        }
    }
}
