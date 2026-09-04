package io.orchesty.sdk.exception;

/**
 * Signals a permanent, non-retryable failure.
 * Translated by the error handler into a {@code STOP_AND_FAILED} result-code so the
 * Bridge stops the message.
 */
public class OnStopAndFailException extends RuntimeException {

    public OnStopAndFailException(String message) {
        super(message);
    }

    public OnStopAndFailException(String message, Throwable cause) {
        super(message, cause);
    }
}
