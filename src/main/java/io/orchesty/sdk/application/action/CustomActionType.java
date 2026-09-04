package io.orchesty.sdk.application.action;

/** Kind of a {@link CustomAction}: opening a link or calling a topology node. */
public enum CustomActionType {
    OPEN("open"),
    CALL("call");

    private final String value;

    CustomActionType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
