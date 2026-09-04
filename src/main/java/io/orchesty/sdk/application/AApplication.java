package io.orchesty.sdk.application;

import io.orchesty.sdk.application.action.CustomAction;
import io.orchesty.sdk.application.enums.ApplicationType;
import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.application.form.Field;
import io.orchesty.sdk.application.form.FieldType;
import io.orchesty.sdk.application.form.Form;
import io.orchesty.sdk.application.form.FormStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for applications.
 * Merges stored settings into the form stack for rendering in the Orchesty UI and prunes
 * saved settings down to the fields the form stack declares.
 */
public abstract class AApplication implements IApplication {

    protected boolean installable = true;

    @Override
    public abstract String getName();

    @Override
    public abstract String getPublicName();

    @Override
    public abstract String getDescription();

    @Override
    public abstract String getAuthorizationType();

    @Override
    public abstract FormStack getFormStack();

    @Override
    public abstract boolean isAuthorized(ApplicationInstall applicationInstall);

    @Override
    public String getApplicationType() {
        return ApplicationType.CRON;
    }

    @Override
    public boolean isInstallable() {
        return installable;
    }

    @Override
    public String getLogo() {
        return null;
    }

    @Override
    public String getInfo() {
        return "";
    }

    @Override
    public Map<String, Object> getApplicationForms(ApplicationInstall applicationInstall) {
        Map<String, Object> settings = applicationInstall.getSettings();
        FormStack formStack = getFormStack();

        for (Form form : formStack.getForms()) {
            Map<String, Object> formSettings = asMap(settings.get(form.getKey()));
            if (formSettings == null) {
                continue;
            }
            for (Field field : form.getFields()) {
                if (formSettings.containsKey(field.getKey())) {
                    Object value = formSettings.get(field.getKey());
                    if (field.getType() == FieldType.PASSWORD) {
                        field.setValue(true);
                    } else if (field.getType() == FieldType.MULTI_SELECT
                            && value instanceof Map<?, ?> m
                            && m.isEmpty()) {
                        // PHP serializes [] and {} alike; an empty multi-select must reach the UI as a list.
                        field.setValue(List.of());
                    } else {
                        field.setValue(value);
                    }
                }
            }
        }

        customFormReplace(formStack, applicationInstall);
        autoInjectLimitForm(formStack, applicationInstall);
        postProcessForms(formStack, applicationInstall);
        return formStack.toArray();
    }

    @Override
    public ApplicationInstall saveApplicationForms(
            ApplicationInstall applicationInstall, Map<String, Object> settings) {
        FormStack formStack = getFormStack();
        customFormReplace(formStack, applicationInstall);
        autoInjectLimitForm(formStack, applicationInstall);

        Map<String, List<String>> definedFields = new LinkedHashMap<>();
        Map<String, Object> preparedSettings = new LinkedHashMap<>();

        for (Form form : formStack.getForms()) {
            definedFields.put(form.getKey(), new ArrayList<>());
            Map<String, Object> incoming = asMap(settings.get(form.getKey()));
            for (Field field : form.getFields()) {
                definedFields.get(form.getKey()).add(field.getKey());
                if (incoming != null && incoming.containsKey(field.getKey())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prepForm = (Map<String, Object>)
                            preparedSettings.computeIfAbsent(form.getKey(), k -> new LinkedHashMap<String, Object>());
                    prepForm.put(field.getKey(), incoming.get(field.getKey()));
                }
            }
        }

        // The authorization_form always keeps a 'token' field (OAuth compatibility).
        List<String> authFields = definedFields.computeIfAbsent(CoreForm.AUTHORIZATION_FORM, k -> new ArrayList<>());
        if (!authFields.contains("token")) {
            authFields.add("token");
        }

        if (!preparedSettings.isEmpty()) {
            applicationInstall.addSettings(preparedSettings);
        }

        // Prune persisted settings down to fields declared by the form stack.
        Map<String, Object> appSettings = applicationInstall.getSettings();
        for (String formKey : new ArrayList<>(appSettings.keySet())) {
            if (!definedFields.containsKey(formKey)) {
                appSettings.remove(formKey);
            } else {
                Map<String, Object> formSettings = asMap(appSettings.get(formKey));
                if (formSettings != null) {
                    List<String> allowed = definedFields.get(formKey);
                    for (String fieldKey : new ArrayList<>(formSettings.keySet())) {
                        if (!allowed.contains(fieldKey)) {
                            formSettings.remove(fieldKey);
                        }
                    }
                }
            }
        }
        applicationInstall.setSettings(appSettings);
        return applicationInstall;
    }

    @Override
    public ApplicationInstall savePassword(
            ApplicationInstall applicationInstall, String formKey, String fieldKey, String password) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put(fieldKey, password);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put(formKey, field);
        return applicationInstall.addSettings(form);
    }

    @Override
    public Map<String, Object> toArray() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", getPublicName());
        out.put("authorization_type", getAuthorizationType());
        out.put("application_type", getApplicationType());
        out.put("key", getName());
        out.put("description", getDescription());
        out.put("info", getInfo());
        out.put("logo", getLogo());
        out.put("isInstallable", isInstallable());
        return out;
    }

    /**
     * Hook for applications that need to modify the form stack at render and save time.
     *
     * @param forms form stack to modify
     * @param applicationInstall application install being rendered or saved
     */
    protected void customFormReplace(FormStack forms, ApplicationInstall applicationInstall) {
        // default: no-op
    }

    /**
     * Render-only hook applied after the limiter form is injected but before serialization.
     * Used to append informational (read-only) fields that must not be persisted, e.g. the
     * OAuth2 redirect URL. Not called when saving.
     *
     * @param forms form stack to modify
     * @param applicationInstall application install being rendered
     */
    protected void postProcessForms(FormStack forms, ApplicationInstall applicationInstall) {
        // default: no-op
    }

    /**
     * Returns the global (code-level) limiter defaults used when the install has no limiter
     * form values. The default implementation returns an empty map.
     *
     * @param applicationInstall application install the limits are resolved for
     * @return limiter settings ({@code useLimit}, {@code value}, {@code time}), or an empty map
     */
    public Map<String, Object> getGlobalLimits(ApplicationInstall applicationInstall) {
        return new LinkedHashMap<>();
    }

    /**
     * Returns the custom actions shown on the application detail in the UI.
     * The default implementation returns none.
     *
     * @return the custom actions
     */
    public List<CustomAction> getCustomActions() {
        return new ArrayList<>();
    }

    protected void autoInjectLimitForm(FormStack forms, ApplicationInstall applicationInstall) {
        Form limiterForm = null;
        for (Form f : forms.getForms()) {
            if (CoreForm.LIMITER_FORM.equals(f.getKey())) {
                limiterForm = f;
                break;
            }
        }
        if (limiterForm == null) {
            limiterForm = new Form(CoreForm.LIMITER_FORM, CoreForm.getFormName(CoreForm.LIMITER_FORM));
            forms.addForm(limiterForm);
        }

        Map<String, Object> limiter = asMap(applicationInstall.getSettings().get(CoreForm.LIMITER_FORM));
        Object useLimit = limiter != null ? limiter.getOrDefault("useLimit", false) : false;
        limiterForm.addField(new Field(FieldType.CHECKBOX, "useLimit", "Use limit", useLimit));

        Object value = limiter != null ? limiter.get("value") : null;
        limiterForm.addField(new Field(FieldType.NUMBER, "value", "Limit per time", value));

        Object time = limiter != null ? limiter.get("time") : null;
        limiterForm.addField(new Field(FieldType.NUMBER, "time", "Time in seconds", time));
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }
}
