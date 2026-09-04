package io.orchesty.sdk.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import io.orchesty.sdk.process.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP response holding the raw body string, status code, reason and headers.
 * Provides helpers to parse the body as JSON.
 */
public class ResponseDto implements IResponseDto {

    private final String body;
    private final int responseCode;
    private final String reason;
    private final Map<String, String> headers;

    public ResponseDto(String body, int responseCode, String reason, Map<String, String> headers) {
        this.body = body == null ? "" : body;
        this.responseCode = responseCode;
        this.reason = reason;
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    @Override
    public String getBody() {
        return body;
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public String getReason() {
        return reason;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Parses the body as a JSON object; an empty body yields an empty map.
     *
     * @return the parsed JSON object
     */
    public Map<String, Object> getJsonBody() {
        String b = (body == null || body.isEmpty()) ? "{}" : body;
        return Json.fromString(b, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    /**
     * Parses the JSON body as the given type; an empty body is parsed as {@code {}}.
     *
     * @param type target type
     * @param <T> target type
     * @return the parsed body
     */
    public <T> T getJsonBody(Class<T> type) {
        String b = (body == null || body.isEmpty()) ? "{}" : body;
        return Json.fromString(b, type);
    }
}
