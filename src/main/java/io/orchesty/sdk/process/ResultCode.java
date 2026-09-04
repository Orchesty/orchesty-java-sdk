package io.orchesty.sdk.process;

/**
 * Result codes understood by the Bridge.
 * The integer {@link #code()} is what the Bridge reads from the {@code result-code} header.
 */
public enum ResultCode {
    SUCCESS(0),

    REPEAT(1001),
    FORWARD_TO_TARGET_QUEUE(1002),
    DO_NOT_CONTINUE(1003),
    LIMIT_EXCEEDED(1004),
    STOP_AND_FAILED(1006),

    BATCH_CURSOR_WITH_FOLLOWERS(1010),
    BATCH_CURSOR_ONLY(1011),

    UNKNOWN_ERROR(2001),
    INVALID_HEADERS(2002),
    INVALID_CONTENT(2003);

    private final int code;

    ResultCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean isSuccess() {
        switch (this) {
            case SUCCESS:
            case REPEAT:
            case FORWARD_TO_TARGET_QUEUE:
            case DO_NOT_CONTINUE:
            case BATCH_CURSOR_ONLY:
            case BATCH_CURSOR_WITH_FOLLOWERS:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSuccess(int code) {
        return fromCode(code).isSuccess();
    }

    public static ResultCode fromCode(int code) {
        for (ResultCode rc : values()) {
            if (rc.code == code) {
                return rc;
            }
        }
        return UNKNOWN_ERROR;
    }
}
