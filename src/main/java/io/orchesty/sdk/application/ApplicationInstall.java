package io.orchesty.sdk.application;

import io.orchesty.sdk.storage.ADocument;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persisted record of an application installed for a given user and SDK.
 * The {@code settings} map is held decrypted in memory; {@link ApplicationInstallRepository}
 * encrypts it into {@code encryptedSettings} before persisting it in the Worker-API
 * document store (collection {@value #COLLECTION}).
 */
public class ApplicationInstall extends ADocument {

    public static final String COLLECTION = "ApplicationInstall";

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationInstall.class);

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private String user = "";
    private String key = "";
    private String sdk = "";
    private Map<String, Object> settings;
    private boolean enabled;
    private boolean deleted;
    private Instant created = Instant.now();
    private Instant updated = Instant.now();
    private String encryptedSettings = "";
    private Map<String, Object> nonEncryptedSettings = new LinkedHashMap<>();
    private Instant expires;

    @Override
    public String getCollection() {
        return COLLECTION;
    }

    @Override
    public ApplicationInstall setId(String id) {
        this.id = id;
        return this;
    }

    public String getUser() {
        return user;
    }

    public ApplicationInstall setUser(String user) {
        this.user = user;
        return this;
    }

    public String getName() {
        return key;
    }

    public ApplicationInstall setName(String name) {
        this.key = name;
        return this;
    }

    public String getSdk() {
        return sdk;
    }

    public ApplicationInstall setSdk(String sdk) {
        this.sdk = sdk;
        return this;
    }

    public Map<String, Object> getSettings() {
        return settings == null ? new LinkedHashMap<>() : settings;
    }

    public ApplicationInstall setSettings(Map<String, Object> settings) {
        this.settings = settings;
        return this;
    }

    public ApplicationInstall addSettings(Map<String, Object> setting) {
        this.settings = deepMerge(getSettings(), setting);
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ApplicationInstall setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getEncryptedSettings() {
        return encryptedSettings;
    }

    public ApplicationInstall setEncryptedSettings(String encryptedSettings) {
        this.encryptedSettings = encryptedSettings;
        return this;
    }

    public Map<String, Object> getNonEncryptedSettings() {
        return nonEncryptedSettings;
    }

    public ApplicationInstall setNonEncryptedSettings(Map<String, Object> nonEncryptedSettings) {
        this.nonEncryptedSettings = nonEncryptedSettings;
        return this;
    }

    public Instant getExpires() {
        return expires;
    }

    public ApplicationInstall setExpires(Instant expires) {
        this.expires = expires;
        return this;
    }

    public Instant getCreated() {
        return created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public ApplicationInstall setUpdated() {
        this.updated = Instant.now();
        return this;
    }

    /** Returns the document representation persisted in the Worker-API document store. */
    @Override
    public Map<String, Object> toDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", id);
        doc.put("user", user);
        doc.put("key", key);
        doc.put("sdk", sdk);
        doc.put("enabled", enabled);
        doc.put("deleted", deleted);
        doc.put("created", ISO.format(created));
        doc.put("updated", ISO.format(updated));
        doc.put("encryptedSettings", encryptedSettings);
        doc.put("nonEncryptedSettings", nonEncryptedSettings);
        doc.put("expires", expires == null ? null : ISO.format(expires));
        return doc;
    }

    @SuppressWarnings("unchecked")
    public static ApplicationInstall fromDocument(Map<String, Object> doc) {
        ApplicationInstall e = new ApplicationInstall();
        Object idVal = doc.getOrDefault("_id", doc.get("id"));
        e.id = idVal == null ? "" : String.valueOf(idVal);
        e.user = str(doc.get("user"));
        e.key = str(doc.get("key"));
        e.sdk = str(doc.get("sdk"));
        e.enabled = Boolean.TRUE.equals(doc.get("enabled"));
        e.deleted = Boolean.TRUE.equals(doc.get("deleted"));
        e.encryptedSettings = str(doc.get("encryptedSettings"));
        Object nes = doc.get("nonEncryptedSettings");
        if (nes instanceof Map<?, ?> m) {
            e.nonEncryptedSettings = new LinkedHashMap<>((Map<String, Object>) m);
        }
        // An unparseable created/updated keeps the default; an unparseable expiry is unknown (null),
        // not "now", which would make the token look expired.
        Instant created = parse(doc.get("created"));
        if (created != null) {
            e.created = created;
        }
        Instant updated = parse(doc.get("updated"));
        if (updated != null) {
            e.updated = updated;
        }
        e.expires = parse(doc.get("expires"));
        Object settings = doc.get("settings");
        if (settings instanceof Map<?, ?> m) {
            e.settings = new LinkedHashMap<>((Map<String, Object>) m);
        }
        return e;
    }

    /** Returns the representation used in API responses to the platform and UI. */
    public Map<String, Object> toArray() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("user", user);
        out.put("key", key);
        out.put("sdk", sdk);
        out.put("enabled", enabled);
        out.put("deleted", deleted);
        out.put("nonEncryptedSettings", nonEncryptedSettings);
        out.put("created", DATE_TIME.format(created));
        out.put("update", DATE_TIME.format(updated));
        out.put("expires", expires == null ? null : DATE_TIME.format(expires));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : overlay.entrySet()) {
            Object existing = result.get(e.getKey());
            Object incoming = e.getValue();
            if (existing instanceof Map<?, ?> && incoming instanceof Map<?, ?>) {
                result.put(e.getKey(), deepMerge((Map<String, Object>) existing, (Map<String, Object>) incoming));
            } else if (incoming instanceof List<?>) {
                // arrays are replaced, not concatenated
                result.put(e.getKey(), incoming);
            } else {
                result.put(e.getKey(), incoming);
            }
        }
        return result;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Instant parse(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        try {
            return Instant.parse(s);
        } catch (RuntimeException e) {
            try {
                return ISO.parse(s, Instant::from);
            } catch (RuntimeException ex) {
                LOG.warn("[application] Ignoring unparseable date [{}] in ApplicationInstall.", s);
                return null;
            }
        }
    }
}
