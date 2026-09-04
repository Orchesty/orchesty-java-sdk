package io.orchesty.sdk.transport;

import java.util.List;

/**
 * Declarative status-code routing for an outbound HTTP call, with common presets.
 *
 * <p>A status is matched against {@link #getSuccess()} (return the response),
 * {@link #getStopAndFail()} (throw {@code OnStopAndFailException}) and
 * {@link #getRepeat()} (throw {@code OnRepeatException}); an unmatched status falls
 * back to success when {@code < 300} and repeat otherwise.
 */
public class ResultRanges {

    private final List<Object> success;
    private final List<Object> stopAndFail;
    private final List<Object> repeat;

    public ResultRanges(List<Object> success, List<Object> stopAndFail, List<Object> repeat) {
        this.success = success;
        this.stopAndFail = stopAndFail;
        this.repeat = repeat;
    }

    /** Default ranges: success {@code <300}, repeat {@code 408,429,>=500}, otherwise stop and fail. */
    public static final ResultRanges DEFAULT =
            new ResultRanges(List.of("<300"), List.of("300-408", "409-429", "430-500"), List.of(408, 429, ">=500"));

    /** Same as {@link #DEFAULT}, but 404 is treated as a success. */
    public static final ResultRanges CONTINUE_ON_NOT_FOUND = new ResultRanges(
            List.of("<300", 404), List.of("300-404", "405-408", "409-429", "430-500"), List.of(408, 429, ">=500"));

    /** Ranges treating every status as a success. */
    public static final ResultRanges KEEP_ALL = new ResultRanges(List.of("<600"), List.of(), List.of());

    public List<Object> getSuccess() {
        return success;
    }

    public List<Object> getStopAndFail() {
        return stopAndFail;
    }

    public List<Object> getRepeat() {
        return repeat;
    }
}
