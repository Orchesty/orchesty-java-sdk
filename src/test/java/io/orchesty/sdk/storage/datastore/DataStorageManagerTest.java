package io.orchesty.sdk.storage.datastore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.orchesty.sdk.storage.file.FileSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests concurrent writes and file-name validation of the file-backed data storage. */
class DataStorageManagerTest {

    private final FileSystem fileSystem = new FileSystem();
    private final DataStorageManager storage = new DataStorageManager(fileSystem);
    private final String id = "test-" + UUID.randomUUID();

    @AfterEach
    void tearDown() {
        fileSystem.delete(id);
    }

    @Test
    void concurrentStoresLoseNoRecords() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                int n = i;
                futures.add(pool.submit(() -> storage.store(id, List.of(Map.of("n", n)), "app", "user")));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertEquals(200, storage.load(id).size());
    }

    @Test
    void locksAreAFixedSetAndStablePerId() {
        assertSame(DataStorageManager.lockFor(id), DataStorageManager.lockFor(id));

        Set<ReentrantLock> locks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 10_000; i++) {
            locks.add(DataStorageManager.lockFor("id-" + i));
        }
        assertTrue(locks.size() <= 64, "locks: " + locks.size());
    }

    @Test
    void namesOutsideTheDataDirectoryAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> storage.store("../escape", List.of("x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> fileSystem.read("nested/file"));
        assertThrows(IllegalArgumentException.class, () -> fileSystem.delete(""));
    }
}
