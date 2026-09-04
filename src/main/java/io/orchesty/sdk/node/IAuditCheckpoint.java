package io.orchesty.sdk.node;

import io.orchesty.sdk.process.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative business audit checkpoint.
 * Serialized into the {@code audit-checkpoint} response header and turned into a
 * structured audit log by the Bridge.
 *
 * <p>{@link #getFields()} is a required allowlist ({@code []} = marker only, logs no
 * payload). {@link #getEntityPath()} defaults to {@code $} (root).
 */
public interface IAuditCheckpoint {

    /** One of {@code process_entry}, {@code process_step}, {@code process_exit}. */
    String getRole();

    default String getEntityPath() {
        return "$";
    }

    List<String> getFields();

    default String toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", getRole());
        out.put("entityPath", getEntityPath());
        out.put("fields", getFields());
        return Json.toString(out);
    }
}
