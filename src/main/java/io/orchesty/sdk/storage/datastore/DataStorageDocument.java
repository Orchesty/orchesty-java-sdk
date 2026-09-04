package io.orchesty.sdk.storage.datastore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single record in the local file-backed {@link DataStorageManager}.
 * Holds an optional {@code user}/{@code application} scope, a creation timestamp and the payload.
 */
public class DataStorageDocument<T> {

    private String user;
    private String application;
    private Instant created = Instant.now();
    private T data;

    public String getUser() {
        return user;
    }

    public DataStorageDocument<T> setUser(String user) {
        this.user = user;
        return this;
    }

    public String getApplication() {
        return application;
    }

    public DataStorageDocument<T> setApplication(String application) {
        this.application = application;
        return this;
    }

    public Instant getCreated() {
        return created;
    }

    public DataStorageDocument<T> setCreated(Instant created) {
        this.created = created;
        return this;
    }

    public T getData() {
        return data;
    }

    public DataStorageDocument<T> setData(T data) {
        this.data = data;
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", user);
        out.put("application", application);
        out.put("created", created == null ? null : created.toString());
        out.put("data", data);
        return out;
    }
}
