package io.orchesty.sdk.transport;

import io.orchesty.sdk.process.Json;
import io.orchesty.sdk.process.ProcessDto;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable outbound HTTP request.
 * Carries url/method/body/headers/timeout plus optional basic-auth credentials and
 * the correlation {@link ProcessDto} ({@code debugInfo}).
 *
 * <p>Auth is app-specific: applications add {@code Authorization: Bearer ...} headers
 * (OAuth2) or basic-auth credentials via {@link #setAuth(String, String)} inside their
 * {@code getRequestDto(...)} — the {@link HttpSender} does not inject auth by itself.
 */
public class RequestDto implements IRequestDto {

    /** Basic-auth credentials pair. */
    public record Auth(String username, String password) {}

    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private String url;
    private HttpMethods method;
    private Object body;
    private Map<String, String> headers = new LinkedHashMap<>();
    private int timeout = DEFAULT_TIMEOUT_MS;
    private ProcessDto debugInfo;
    private Auth auth;

    public RequestDto(String url, HttpMethods method, ProcessDto debugInfo) {
        this(url, method, debugInfo, null, null);
    }

    public RequestDto(String url, HttpMethods method, ProcessDto debugInfo, Object body) {
        this(url, method, debugInfo, body, null);
    }

    public RequestDto(String url, HttpMethods method, ProcessDto debugInfo, Object body, Map<String, String> headers) {
        this.url = url;
        this.method = method;
        this.debugInfo = debugInfo;
        this.body = body;
        if (headers != null) {
            this.headers.putAll(headers);
        }
    }

    @Override
    public String getUrl() {
        return url;
    }

    public RequestDto setUrl(String url) {
        this.url = url;
        return this;
    }

    @Override
    public HttpMethods getMethod() {
        return method;
    }

    public RequestDto setMethod(HttpMethods method) {
        this.method = method;
        return this;
    }

    @Override
    public Object getBody() {
        return body;
    }

    public RequestDto setBody(Object body) {
        this.body = body;
        return this;
    }

    /**
     * Serializes the given value as the JSON body and sets the {@code Content-Type: application/json} header.
     *
     * @param body value to serialize as JSON
     * @return this request
     */
    public RequestDto setJsonBody(Object body) {
        this.body = Json.toString(body);
        this.headers.put("Content-Type", "application/json");
        return this;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    public RequestDto setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
        return this;
    }

    public RequestDto addHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public RequestDto addHeaders(Map<String, String> extra) {
        if (extra != null) {
            this.headers.putAll(extra);
        }
        return this;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    public RequestDto setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public ProcessDto getDebugInfo() {
        return debugInfo;
    }

    public RequestDto setDebugInfo(ProcessDto debugInfo) {
        this.debugInfo = debugInfo;
        return this;
    }

    /**
     * Configures HTTP basic authentication for this request.
     *
     * @param username basic-auth user name
     * @param password basic-auth password
     * @return this request
     */
    public RequestDto setAuth(String username, String password) {
        this.auth = new Auth(username, password);
        return this;
    }

    public Auth getAuth() {
        return auth;
    }
}
