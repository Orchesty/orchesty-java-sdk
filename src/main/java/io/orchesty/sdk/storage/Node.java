package io.orchesty.sdk.storage;

import io.orchesty.sdk.process.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Topology node document stored in the {@code Node} collection.
 * Carries {@code systemConfigs} (a JSON string) whose {@code repeater} block the
 * error handler consults to override the per-node repeat interval/hops.
 */
public class Node extends ADocument {

    public static final String COLLECTION = "Node";

    private String systemConfigs;

    @Override
    public String getCollection() {
        return COLLECTION;
    }

    public String getSystemConfigs() {
        return systemConfigs;
    }

    public Node setSystemConfigs(String systemConfigs) {
        this.systemConfigs = systemConfigs;
        return this;
    }

    /**
     * Parses the {@code systemConfigs} JSON string.
     *
     * @return the parsed configuration, or {@code null} if absent or invalid
     */
    public Map<String, Object> getSystemConfigsParsed() {
        if (systemConfigs == null || systemConfigs.isEmpty()) {
            return null;
        }
        try {
            return Json.fromString(systemConfigs, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Returns the {@code repeater} block of {@code systemConfigs}.
     *
     * @return the repeater settings, or {@code null} if not configured
     */
    @SuppressWarnings("unchecked")
    public Repeater getRepeater() {
        Map<String, Object> configs = getSystemConfigsParsed();
        if (configs == null) {
            return null;
        }
        Object repeater = configs.get("repeater");
        if (!(repeater instanceof Map<?, ?> m)) {
            return null;
        }
        Map<String, Object> r = (Map<String, Object>) m;
        boolean enabled = Boolean.TRUE.equals(r.get("enabled"));
        int hops = toInt(r.get("hops"), 0);
        int interval = toInt(r.get("interval"), 0);
        return new Repeater(enabled, hops, interval);
    }

    @Override
    public Map<String, Object> toDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", id);
        doc.put("systemConfigs", systemConfigs);
        return doc;
    }

    public static Node fromDocument(Map<String, Object> doc) {
        Node node = new Node();
        Object idVal = doc.getOrDefault("_id", doc.get("id"));
        node.id = idVal == null ? "" : String.valueOf(idVal);
        Object sc = doc.get("systemConfigs");
        node.systemConfigs = sc == null ? null : String.valueOf(sc);
        return node;
    }

    private static int toInt(Object value, int def) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? def : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Per-node repeater override read from {@code systemConfigs.repeater}. */
    public record Repeater(boolean enabled, int hops, int interval) {}
}
