package io.orchesty.sdk.application.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered collection of {@link Form}s that make up an application's settings. */
public class FormStack {

    private final List<Form> forms = new ArrayList<>();

    public List<Form> getForms() {
        return forms;
    }

    public FormStack addForm(Form form) {
        forms.add(form);
        return this;
    }

    public Map<String, Object> toArray() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Form form : forms) {
            out.put(form.getKey(), form.toArray());
        }
        return out;
    }
}
