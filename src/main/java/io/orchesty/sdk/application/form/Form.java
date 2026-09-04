package io.orchesty.sdk.application.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A named group of {@link Field}s rendered as one settings form in the Orchesty UI. */
public class Form {

    private final String key;
    private String publicName;
    private String description = "";
    private boolean readOnly;
    private final List<Field> fields = new ArrayList<>();

    public Form(String key, String publicName) {
        this.key = key;
        this.publicName = publicName;
    }

    public String getKey() {
        return key;
    }

    public String getPublicName() {
        return publicName;
    }

    public Form setPublicName(String publicName) {
        this.publicName = publicName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Form setDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public Form setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public List<Field> getFields() {
        return fields;
    }

    public Form addField(Field field) {
        fields.add(field);
        return this;
    }

    public Map<String, Object> toArray() {
        List<Object> fieldArrays = new ArrayList<>();
        for (Field f : fields) {
            fieldArrays.add(f.toArray());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("publicName", publicName);
        out.put("description", description);
        out.put("readOnly", readOnly);
        out.put("fields", fieldArrays);
        return out;
    }
}
