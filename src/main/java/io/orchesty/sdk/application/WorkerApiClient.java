package io.orchesty.sdk.application;

import io.orchesty.sdk.process.Headers;
import io.orchesty.sdk.process.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for the Orchesty Worker-API document store.
 * Sends JSON bodies with the {@code orchesty-api-key} header to {@code <WORKER_API_HOST><path>}.
 */
public class WorkerApiClient {

    public record Response(int status, String body) {}

    private final String host;
    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WorkerApiClient(String host, String apiKey) {
        this.host = host;
        this.apiKey = apiKey;
    }

    public Response send(String path, String method, Object data) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(host + path))
                    .timeout(Duration.ofSeconds(10))
                    .header(Headers.ORCHESTY_API_KEY, apiKey)
                    .header("Content-Type", "application/json");

            HttpRequest.BodyPublisher body = data == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(Json.toString(data));

            HttpRequest request = builder.method(method, body).build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(res.statusCode(), res.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Worker-API request " + method + " " + path + " failed: " + describe(e), e);
        }
    }

    /**
     * Describes an exception and its causes as a single string of class names and messages.
     * JDK network exceptions often carry a null message (e.g. ConnectException caused by
     * UnresolvedAddressException), so the class names are always included.
     *
     * @param e the exception to describe
     * @return the description, truncated after roughly 300 characters
     */
    static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 300; t = t.getCause()) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                sb.append(": ").append(t.getMessage());
            }
        }
        return sb.toString();
    }
}
