package io.orchesty.sdk.transport;

/**
 * Optional hook invoked by {@link HttpSender} after every outbound HTTP call to record curl metrics.
 * Implemented by the metrics module; the transport layer stays independent of it.
 */
@FunctionalInterface
public interface CurlMetricsRecorder {

    /**
     * Records the metrics of a single outbound HTTP call.
     *
     * @param request the outbound request (carries {@code debugInfo} correlation)
     * @param responseCode the HTTP status (or a synthetic code such as 408/500 on error)
     * @param durationMillis wall-clock duration of the call in milliseconds
     * @param responseError error body/message when {@code responseCode >= 300}, else {@code null}
     */
    void record(RequestDto request, int responseCode, long durationMillis, String responseError);
}
