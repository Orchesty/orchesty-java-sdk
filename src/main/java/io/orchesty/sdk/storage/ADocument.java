package io.orchesty.sdk.storage;

import java.util.Map;

/**
 * Base class for documents persisted through the Worker-API document store.
 * Provides an identity, a collection name and a document serialization used by {@link Repository}.
 */
public abstract class ADocument {

    protected String id = "";

    public String getId() {
        return id;
    }

    public ADocument setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Returns the Worker-API collection name used in {@code /document/<collection>}.
     *
     * @return the collection name
     */
    public abstract String getCollection();

    /**
     * Serializes this document for the Worker-API document store.
     *
     * @return the document map, typically keyed by {@code _id}
     */
    public abstract Map<String, Object> toDocument();
}
