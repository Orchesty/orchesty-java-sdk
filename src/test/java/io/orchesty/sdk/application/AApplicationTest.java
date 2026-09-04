package io.orchesty.sdk.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.application.form.Field;
import io.orchesty.sdk.application.form.FieldType;
import io.orchesty.sdk.application.form.Form;
import io.orchesty.sdk.application.form.FormStack;
import io.orchesty.sdk.crypt.PhpSerializer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AApplicationTest {

    static final class DemoApp extends ABasicApplication {
        @Override
        public String getName() {
            return "demo";
        }

        @Override
        public String getPublicName() {
            return "Demo App";
        }

        @Override
        public String getDescription() {
            return "demo";
        }

        @Override
        public FormStack getFormStack() {
            Form auth = new Form(CoreForm.AUTHORIZATION_FORM, "Authorization");
            auth.addField(new Field(FieldType.TEXT, "host", "Host", null, true));
            auth.addField(new Field(FieldType.PASSWORD, "password", "Password", null, true));
            return new FormStack().addForm(auth);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendersFormsWithPasswordMaskedAndValuesMerged() {
        DemoApp app = new DemoApp();
        ApplicationInstall install =
                new ApplicationInstall().setUser("u").setName("demo").setSdk("s");
        Map<String, Object> authForm = new LinkedHashMap<>();
        authForm.put("host", "sftp.example.com");
        authForm.put("password", "secret");
        install.addSettings(Map.of(CoreForm.AUTHORIZATION_FORM, authForm));

        Map<String, Object> forms = app.getApplicationForms(install);
        Map<String, Object> auth = (Map<String, Object>) forms.get(CoreForm.AUTHORIZATION_FORM);
        var fields = (java.util.List<Map<String, Object>>) auth.get("fields");

        Map<String, Object> host = fields.stream()
                .filter(f -> f.get("key").equals("host"))
                .findFirst()
                .orElseThrow();
        Map<String, Object> password = fields.stream()
                .filter(f -> f.get("key").equals("password"))
                .findFirst()
                .orElseThrow();

        assertEquals("sftp.example.com", host.get("value"));
        // password never leaves the worker as plaintext - masked as boolean true
        assertEquals(Boolean.TRUE, password.get("value"));

        // limiter form auto-injected
        assertTrue(forms.containsKey(CoreForm.LIMITER_FORM));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveApplicationFormsPrunesUnknownFieldsAndKeepsToken() {
        DemoApp app = new DemoApp();
        ApplicationInstall install =
                new ApplicationInstall().setUser("u").setName("demo").setSdk("s");

        Map<String, Object> incomingAuth = new LinkedHashMap<>();
        incomingAuth.put("host", "h");
        incomingAuth.put("password", "p");
        incomingAuth.put("bogus", "should-be-pruned");
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put(CoreForm.AUTHORIZATION_FORM, incomingAuth);
        incoming.put("unknown_form", Map.of("x", "y"));

        app.saveApplicationForms(install, incoming);

        Map<String, Object> settings = install.getSettings();
        assertFalse(settings.containsKey("unknown_form"));
        Map<String, Object> auth = (Map<String, Object>) settings.get(CoreForm.AUTHORIZATION_FORM);
        assertEquals("h", auth.get("host"));
        assertEquals("p", auth.get("password"));
        assertFalse(auth.containsKey("bogus"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void lifecycleCallbacksReturnOkByDefault() {
        DemoApp app = new DemoApp();
        Map<String, Object> body = Map.of("user", "u", "name", "demo", "sdk", "s");
        for (String method : new String[] {
            "afterInstallCallback", "afterEnableCallback", "afterDisableCallback", "afterUninstallCallback"
        }) {
            Object res = app.runSynchronousAction(method, body);
            assertEquals("ok", ((Map<String, Object>) res).get("status"), method);
        }
    }

    @Test
    void unknownSyncActionThrows() {
        DemoApp app = new DemoApp();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> app.runSynchronousAction("doesNotExist", Map.of()));
    }

    @Test
    void authorizationReflectsRequiredFields() {
        DemoApp app = new DemoApp();
        ApplicationInstall empty = new ApplicationInstall().setName("demo");
        assertFalse(app.isAuthorized(empty));

        ApplicationInstall filled = new ApplicationInstall().setName("demo");
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("host", "h");
        auth.put("password", "p");
        filled.addSettings(Map.of(CoreForm.AUTHORIZATION_FORM, auth));
        assertTrue(app.isAuthorized(filled));
    }

    @Test
    void emptyMultiSelectIsRenderedAsListEvenAfterPhpRoundTrip() {
        MultiSelectApp app = new MultiSelectApp();
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("empty", List.of());
        stored.put("picked", List.of("a"));
        stored.put("options", Map.of());
        // Settings are stored PHP-serialized, where [] and {} are the same and read back as a map.
        @SuppressWarnings("unchecked")
        Map<String, Object> roundTripped =
                (Map<String, Object>) PhpSerializer.unserialize(PhpSerializer.serialize(Map.of("settings", stored)));
        ApplicationInstall install = new ApplicationInstall().setName("demo").setSettings(roundTripped);

        Map<String, Object> values = fieldValues(app.getApplicationForms(install), "settings");

        assertEquals(List.of(), values.get("empty"));
        assertEquals(List.of("a"), values.get("picked"));
        assertEquals(Map.of(), values.get("options"));
    }

    static final class MultiSelectApp extends ABasicApplication {
        @Override
        public String getName() {
            return "multi";
        }

        @Override
        public String getPublicName() {
            return "Multi";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public FormStack getFormStack() {
            Form settings = new Form("settings", "Settings");
            settings.addField(new Field(FieldType.MULTI_SELECT, "empty", "Empty"));
            settings.addField(new Field(FieldType.MULTI_SELECT, "picked", "Picked"));
            settings.addField(new Field(FieldType.TEXT, "options", "Options"));
            return new FormStack().addForm(settings);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldValues(Map<String, Object> forms, String formKey) {
        Map<String, Object> form = (Map<String, Object>) forms.get(formKey);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map<String, Object> field : (List<Map<String, Object>>) form.get("fields")) {
            values.put((String) field.get("key"), field.get("value"));
        }
        return values;
    }
}
