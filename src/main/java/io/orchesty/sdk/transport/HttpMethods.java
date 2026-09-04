package io.orchesty.sdk.transport;

/**
 * HTTP methods supported by the native transport.
 */
public enum HttpMethods {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    OPTIONS,
    HEAD;

    /**
     * Parses a method name case-insensitively, defaulting to {@link #GET} when it is null or empty.
     *
     * @param method method name to parse
     * @return the matching HTTP method
     */
    public static HttpMethods parse(String method) {
        if (method == null || method.isEmpty()) {
            return GET;
        }
        try {
            return HttpMethods.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP method [" + method + "].");
        }
    }
}
