package io.orchesty.sdk.storage.file;

import io.orchesty.sdk.process.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON file store with atomic writes.
 * Writes go to a unique temporary file that is atomically moved into place; files live in
 * {@code <java.io.tmpdir>/orchesty/data} with temporary files in {@code <java.io.tmpdir>/orchesty/tmp}.
 * A file name that would resolve outside the data directory (e.g. {@code ../x} or {@code a/b})
 * is rejected with an {@link IllegalArgumentException}.
 */
public class FileSystem {

    private static final Path DATA_DIR = Path.of(System.getProperty("java.io.tmpdir"), "orchesty", "data");
    private static final Path TMP_DIR = Path.of(System.getProperty("java.io.tmpdir"), "orchesty", "tmp");

    public boolean write(String file, List<Map<String, Object>> data) {
        Path target = getFilePath(file, false);
        Path tmp = null;
        try {
            Files.createDirectories(DATA_DIR);
            Files.createDirectories(TMP_DIR);
            tmp = Files.createTempFile(TMP_DIR, file + "-", ".json");
            Files.writeString(tmp, Json.toString(data), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new IllegalStateException("Failed to write data file [" + file + "]: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> read(String file) {
        Path path = getFilePath(file, false);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isEmpty()) {
                return List.of();
            }
            return Json.fromString(content, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read data file [" + file + "]: " + e.getMessage(), e);
        }
    }

    public boolean delete(String file) {
        try {
            return Files.deleteIfExists(getFilePath(file, false));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete data file [" + file + "]: " + e.getMessage(), e);
        }
    }

    public Path getFilePath(String file, boolean tmp) {
        Path dir = tmp ? TMP_DIR : DATA_DIR;
        Path path = dir.resolve(file + ".json").normalize();
        if (file == null || file.isEmpty() || !dir.equals(path.getParent())) {
            throw new IllegalArgumentException("Invalid data file name [" + file + "].");
        }
        return path;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort; the original error is reported
        }
    }
}
