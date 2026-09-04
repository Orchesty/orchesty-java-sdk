package io.orchesty.sdk.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.crypt.CryptManager;
import io.orchesty.sdk.crypt.WindWalkerCrypt;
import io.orchesty.sdk.process.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationInstallRepositoryTest {

    private Javalin stub;
    private int port;
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    private ApplicationInstallRepository repository;

    @BeforeEach
    void setUp() {
        store.clear();
        stub = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.routes.post("/document/{collection}", this::saveDocuments);
            cfg.routes.get("/document/{collection}", this::findDocuments);
            cfg.routes.delete("/document/{collection}", this::deleteDocuments);
        });
        stub.start(0);
        port = stub.port();

        CryptManager crypt = new CryptManager(List.of(new WindWalkerCrypt("repo-test-secret-1234567890")));
        WorkerApiClient client = new WorkerApiClient("http://127.0.0.1:" + port, "");
        repository = new ApplicationInstallRepository(client, crypt);
    }

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.stop();
        }
    }

    @Test
    void settingsAreEncryptedAtRestAndDecryptedOnLoad() {
        ApplicationInstall install =
                new ApplicationInstall().setUser("john").setName("sftp").setSdk("java");
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("host", "sftp.example.com");
        auth.put("password", "hunter2");
        install.addSettings(Map.of(CoreForm.AUTHORIZATION_FORM, auth));

        repository.insert(install);

        // Inspect what was actually stored: plaintext must NOT be present.
        Map<String, Object> storedDoc = store.values().iterator().next();
        String encrypted = String.valueOf(storedDoc.get("encryptedSettings"));
        assertTrue(encrypted.startsWith("002_"), "settings must be stored WindWalker-encrypted");
        assertFalse(Json.toString(storedDoc).contains("hunter2"), "plaintext password must never be persisted");
        assertFalse(storedDoc.containsKey("settings") && storedDoc.get("settings") != null);

        // Load back: repository transparently decrypts.
        ApplicationInstall loaded = repository.findByNameAndUser("sftp", "john", List.of("java"), null);
        assertNotNull(loaded);
        @SuppressWarnings("unchecked")
        Map<String, Object> loadedAuth =
                (Map<String, Object>) loaded.getSettings().get(CoreForm.AUTHORIZATION_FORM);
        assertEquals("sftp.example.com", loadedAuth.get("host"));
        assertEquals("hunter2", loadedAuth.get("password"));
        assertEquals("", loaded.getEncryptedSettings());
    }

    private void saveDocuments(Context ctx) {
        List<Map<String, Object>> docs = Json.fromString(ctx.body(), new TypeReference<>() {});
        for (Map<String, Object> doc : docs) {
            String id = String.valueOf(doc.getOrDefault("_id", ""));
            if (id == null || id.isEmpty()) {
                id = UUID.randomUUID().toString();
                doc.put("_id", id);
            }
            store.put(id, doc);
        }
        ctx.contentType("application/json").result(Json.toString(docs));
    }

    private void findDocuments(Context ctx) {
        Map<String, Object> filter = ctx.queryParam("filter") == null
                ? Map.of()
                : Json.fromString(ctx.queryParam("filter"), new TypeReference<LinkedHashMap<String, Object>>() {});
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> doc : store.values()) {
            if (matches(doc, filter)) {
                result.add(doc);
            }
        }
        ctx.contentType("application/json").result(Json.toString(result));
    }

    private void deleteDocuments(Context ctx) {
        Map<String, Object> filter =
                Json.fromString(ctx.queryParam("filter"), new TypeReference<LinkedHashMap<String, Object>>() {});
        Object ids = filter.get("ids");
        if (ids instanceof List<?> list) {
            list.forEach(id -> store.remove(String.valueOf(id)));
        }
        ctx.contentType("application/json").result("{}");
    }

    @SuppressWarnings("unchecked")
    private static boolean matches(Map<String, Object> doc, Map<String, Object> filter) {
        Object names = filter.get("names");
        if (names instanceof List<?> list && !list.isEmpty() && !list.contains(doc.get("key"))) {
            return false;
        }
        Object users = filter.get("users");
        if (users instanceof List<?> list && !list.isEmpty() && !list.contains(doc.get("user"))) {
            return false;
        }
        return true;
    }
}
