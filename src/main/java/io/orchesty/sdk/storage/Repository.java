package io.orchesty.sdk.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.process.Json;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic HTTP-backed repository over the Worker-API document store.
 * Uses {@code POST /document/<collection>} to upsert, {@code GET ...?filter=&sorter=&paging=} to query
 * and {@code DELETE ...?filter=} to remove. Subclasses implement {@link #fromDocument(Map)} and may
 * override the {@link #beforeSend(ADocument)} / {@link #afterReceive(ADocument)} hooks (e.g. to
 * encrypt/decrypt settings).
 */
public abstract class Repository<T extends ADocument> {

    protected final WorkerApiClient client;
    protected final String collection;

    protected Repository(WorkerApiClient client, String collection) {
        this.client = client;
        this.collection = collection;
    }

    public String getCollection() {
        return collection;
    }

    protected abstract T fromDocument(Map<String, Object> doc);

    /**
     * Hook run before a document is sent, e.g. to encrypt settings.
     *
     * @param entity document about to be sent
     */
    protected void beforeSend(T entity) {
        // default: no-op
    }

    /**
     * Hook run after a document is received or sent, e.g. to decrypt settings.
     *
     * @param entity document that was received or sent
     */
    protected void afterReceive(T entity) {
        // default: no-op
    }

    /**
     * Upserts one document. Worker-API does not return generated ids, so a new entity still has
     * no id afterwards; set the id before inserting or load the entity again if you need it.
     *
     * @param entity document to store
     */
    public void insert(T entity) {
        insertMany(List.of(entity));
    }

    public void update(T entity) {
        insertMany(List.of(entity));
    }

    /**
     * Upserts documents in one request. Generated ids are not assigned back (see
     * {@link #insert(ADocument)}).
     *
     * @param entities documents to store
     */
    public void insertMany(List<T> entities) {
        List<Map<String, Object>> docs = new ArrayList<>();
        for (T e : entities) {
            beforeSend(e);
            docs.add(e.toDocument());
        }
        WorkerApiClient.Response res = client.send("/document/" + collection, "POST", docs);
        if (res.status() < 200 || res.status() >= 300) {
            throw new IllegalStateException(
                    "Failed to persist " + collection + ": HTTP " + res.status() + " " + res.body());
        }
        for (T e : entities) {
            afterReceive(e);
        }
    }

    public List<T> findMany(Map<String, Object> filter) {
        return findMany(filter, null, null);
    }

    public List<T> findMany(Map<String, Object> filter, Map<String, Object> sorter, Map<String, Object> paging) {
        StringBuilder query = new StringBuilder("/document/").append(collection).append("?");
        query.append("filter=").append(encode(Json.toString(filter == null ? Map.of() : filter)));
        if (sorter != null) {
            query.append("&sorter=").append(encode(Json.toString(sorter)));
        }
        if (paging != null) {
            query.append("&paging=").append(encode(Json.toString(paging)));
        }
        WorkerApiClient.Response res = client.send(query.toString(), "GET", null);
        if (res.status() != 200) {
            throw new IllegalStateException(
                    "Failed to query " + collection + ": HTTP " + res.status() + " " + res.body());
        }
        List<Map<String, Object>> data = Json.fromString(
                res.body() == null || res.body().isEmpty() ? "[]" : res.body(), new TypeReference<>() {});
        List<T> entities = new ArrayList<>();
        for (Map<String, Object> doc : data) {
            T entity = fromDocument(doc);
            afterReceive(entity);
            entities.add(entity);
        }
        return entities;
    }

    public T findOne(Map<String, Object> filter) {
        List<T> many = findMany(filter);
        return many.isEmpty() ? null : many.get(0);
    }

    public T findById(String id) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("ids", List.of(id));
        return findOne(filter);
    }

    public void removeMany(Map<String, Object> filter) {
        String query = "/document/" + collection + "?filter=" + encode(Json.toString(filter));
        WorkerApiClient.Response res = client.send(query, "DELETE", null);
        if (res.status() < 200 || res.status() >= 300) {
            throw new IllegalStateException(
                    "Failed to delete from " + collection + ": HTTP " + res.status() + " " + res.body());
        }
    }

    public void remove(T entity) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("ids", List.of(entity.getId()));
        removeMany(filter);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
