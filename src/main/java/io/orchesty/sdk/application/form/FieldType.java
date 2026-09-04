package io.orchesty.sdk.application.form;

import com.fasterxml.jackson.annotation.JsonValue;

/** Form field types the Orchesty UI can render. */
public enum FieldType {
    TEXT("text"),
    NUMBER("number"),
    URL("url"),
    PASSWORD("password"),
    SELECT_BOX("selectbox"),
    MULTI_SELECT("multiselect"),
    CHECKBOX("checkbox");

    private final String value;

    FieldType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
