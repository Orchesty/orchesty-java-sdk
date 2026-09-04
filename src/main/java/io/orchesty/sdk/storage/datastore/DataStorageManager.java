package io.orchesty.sdk.storage.datastore;

import io.orchesty.sdk.storage.file.FileSystem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Multi-tenant file-backed key/value store.
 * Records are grouped by an {@code id} (file name) and optionally filtered by {@code application}/{@code user}.
 * Writes to the same {@code id} are serialized within the JVM, so concurrent stores don't lose
 * records; reads see either the old or the new file because writes are atomic.
 */
public class DataStorageManager {

    private static final int LOCK_STRIPES = 64;

    /**
     * Fixed set of locks shared by all instances (they may use the same files); an id always maps
     * to the same lock, unrelated ids sharing a stripe only wait for each other briefly.
     */
    private static final ReentrantLock[] LOCKS = new ReentrantLock[LOCK_STRIPES];

    static {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            LOCKS[i] = new ReentrantLock();
        }
    }

    private final FileSystem fileSystem;

    public DataStorageManager(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public <T> List<DataStorageDocument<T>> load(String id, String application, String user, int skip, int limit) {
        List<Map<String, Object>> raw = fileSystem.read(id);
        List<DataStorageDocument<T>> matched = new ArrayList<>();
        for (Map<String, Object> doc : raw) {
            if (application != null && !application.equals(doc.get("application"))) {
                continue;
            }
            if (user != null && !user.equals(doc.get("user"))) {
                continue;
            }
            matched.add(fromMap(doc));
        }
        int from = Math.min(Math.max(skip, 0), matched.size());
        int to = limit <= 0 ? matched.size() : Math.min(from + limit, matched.size());
        return matched.subList(from, to);
    }

    public <T> List<DataStorageDocument<T>> load(String id) {
        return load(id, null, null, 0, 0);
    }

    public <T> void store(String id, List<T> data, String application, String user) {
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            List<Map<String, Object>> existing = new ArrayList<>(fileSystem.read(id));
            for (T item : data) {
                DataStorageDocument<T> doc = new DataStorageDocument<T>()
                        .setApplication(application)
                        .setUser(user)
                        .setCreated(Instant.now())
                        .setData(item);
                existing.add(doc.toMap());
            }
            fileSystem.write(id, existing);
        } finally {
            lock.unlock();
        }
    }

    public void remove(String id, String application, String user) {
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            if (application == null && user == null) {
                fileSystem.delete(id);
                return;
            }
            List<Map<String, Object>> raw = fileSystem.read(id);
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Map<String, Object> doc : raw) {
                boolean matchesApp = application == null || application.equals(doc.get("application"));
                boolean matchesUser = user == null || user.equals(doc.get("user"));
                if (!(matchesApp && matchesUser)) {
                    kept.add(doc);
                }
            }
            fileSystem.write(id, kept);
        } finally {
            lock.unlock();
        }
    }

    static ReentrantLock lockFor(String id) {
        return LOCKS[Math.floorMod(id.hashCode(), LOCK_STRIPES)];
    }

    @SuppressWarnings("unchecked")
    private static <T> DataStorageDocument<T> fromMap(Map<String, Object> doc) {
        DataStorageDocument<T> out = new DataStorageDocument<>();
        out.setApplication((String) doc.get("application"));
        out.setUser((String) doc.get("user"));
        Object created = doc.get("created");
        if (created != null) {
            try {
                out.setCreated(Instant.parse(String.valueOf(created)));
            } catch (RuntimeException ignored) {
                // keep default
            }
        }
        out.setData((T) doc.get("data"));
        return out;
    }
}
