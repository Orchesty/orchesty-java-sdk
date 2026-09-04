package io.orchesty.sdk.application;

import io.orchesty.sdk.crypt.CryptManager;
import io.orchesty.sdk.storage.Repository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for {@link ApplicationInstall} documents stored in the Worker-API.
 * Settings are encrypted before saving and decrypted after loading.
 */
public class ApplicationInstallRepository extends Repository<ApplicationInstall> {

    private final CryptManager crypt;

    public ApplicationInstallRepository(WorkerApiClient client, CryptManager crypt) {
        super(client, ApplicationInstall.COLLECTION);
        this.crypt = crypt;
    }

    @Override
    protected ApplicationInstall fromDocument(Map<String, Object> doc) {
        return ApplicationInstall.fromDocument(doc);
    }

    public ApplicationInstall findByNameAndUser(String name, String user, List<String> sdks, Boolean enabled) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("users", List.of(user));
        filter.put("enabled", enabled);
        filter.put("names", List.of(name));
        filter.put("sdks", sdks);
        return findOne(filter);
    }

    public ApplicationInstall findByNameAndUser(String name, String user, List<String> sdks) {
        return findByNameAndUser(name, user, sdks, Boolean.TRUE);
    }

    @Override
    public void remove(ApplicationInstall entity) {
        // ApplicationInstall allows the `enabled` filter and uses null to match regardless of the
        // enabled state, so disabled installs are removed as well.
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("ids", List.of(entity.getId()));
        filter.put("enabled", null);
        removeMany(filter);
    }

    public ApplicationInstall findOneByName(String name, List<String> sdks, Boolean enabled) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("names", List.of(name));
        filter.put("enabled", enabled);
        filter.put("sdks", sdks);
        return findOne(filter);
    }

    @Override
    protected void beforeSend(ApplicationInstall entity) {
        if (!entity.getSettings().isEmpty()) {
            String encrypted = crypt.encrypt(entity.getSettings());
            entity.setEncryptedSettings(encrypted);
            entity.setUpdated();
            entity.setSettings(null);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void afterReceive(ApplicationInstall entity) {
        if (entity.getEncryptedSettings() != null
                && !entity.getEncryptedSettings().isEmpty()) {
            Object decrypted = crypt.decrypt(entity.getEncryptedSettings());
            entity.setSettings(decrypted instanceof Map ? (Map<String, Object>) decrypted : new LinkedHashMap<>());
            entity.setEncryptedSettings("");
        }
    }
}
