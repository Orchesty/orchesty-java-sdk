package io.orchesty.sdk.exception;

/**
 * Signals that a message should be retried later by the Bridge.
 * Carries the repeat {@code interval} (seconds) and {@code maxHops}.
 *
 * <p>The SDK does not retry in-process; throwing this exception is translated by
 * the error handler into a {@code REPEAT} result-code plus the repeater headers,
 * and the Bridge performs the actual re-queue and hop counting.
 */
public class OnRepeatException extends RuntimeException {

    private final int interval;
    private final int maxHops;

    public OnRepeatException(int interval, int maxHops, String message) {
        super(message);
        this.interval = interval;
        this.maxHops = maxHops;
    }

    public OnRepeatException(int interval, int maxHops, String message, Throwable cause) {
        super(message, cause);
        this.interval = interval;
        this.maxHops = maxHops;
    }

    public int getInterval() {
        return interval;
    }

    public int getMaxHops() {
        return maxHops;
    }
}
