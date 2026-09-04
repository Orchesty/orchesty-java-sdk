package io.orchesty.sdk.process;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON envelope exchanged with the Bridge on every worker invocation.
 * Its shape is {@code { "body": "<inner json string>", "headers": { ... } }}, where
 * {@code body} is always a String (the node payload as raw JSON text).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageDto {

    private String body = "{}";
    private Map<String, Object> headers = new LinkedHashMap<>();

    public MessageDto() {}

    public MessageDto(String body, Map<String, Object> headers) {
        this.body = body;
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }
}
