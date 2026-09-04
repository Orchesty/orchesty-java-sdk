package io.orchesty.sdk.application.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single configuration field rendered by the Orchesty UI.
 * {@link #toArray()} produces the JSON shape the UI consumes.
 */
public class Field {

    private final FieldType type;
    private final String key;
    private String label;
    private Object value;
    private boolean required;
    private String description = "";
    private boolean readOnly;
    private boolean disabled;
    private List<Object> choices = new ArrayList<>();

    public Field(FieldType type, String key, String label) {
        this(type, key, label, null, false);
    }

    public Field(FieldType type, String key, String label, Object value) {
        this(type, key, label, value, false);
    }

    public Field(FieldType type, String key, String label, Object value, boolean required) {
        this.type = type;
        this.key = key;
        this.label = label;
        this.value = value;
        this.required = required;
    }

    public FieldType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public Field setValue(Object value) {
        this.value = value;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public Field setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Field setDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    public Field setRequired(boolean required) {
        this.required = required;
        return this;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public Field setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public Field setDisabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }

    public List<Object> getChoices() {
        return choices;
    }

    public Field setChoices(List<Object> choices) {
        this.choices = choices;
        return this;
    }

    public Map<String, Object> toArray() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type.value());
        out.put("key", key);
        out.put("value", value);
        out.put("label", label);
        out.put("description", description);
        out.put("required", required);
        out.put("readOnly", readOnly);
        out.put("disabled", disabled);
        out.put("choices", choices);
        return out;
    }
}
