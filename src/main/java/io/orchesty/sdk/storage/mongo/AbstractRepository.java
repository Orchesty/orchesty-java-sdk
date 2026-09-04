package io.orchesty.sdk.storage.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * Base repository providing CRUD operations over a MongoDB collection.
 * Remaps {@code _id} to {@code id} on read; subclasses convert between {@code T} and {@link Document}.
 *
 * <p>Requires the optional {@code org.mongodb:mongodb-driver-sync} dependency.
 */
public abstract class AbstractRepository<T> {

    protected final MongoCollection<Document> collection;

    protected AbstractRepository(MongoDb client, String collectionName) {
        this.collection = client.getCollection(collectionName);
    }

    protected abstract Document toDocument(T entity);

    protected abstract T fromDocument(Document document);

    /**
     * Returns the entity id used for upserts.
     *
     * @param entity entity to read the id from
     * @return the id, or {@code null} for auto-generated ids
     */
    protected abstract String getId(T entity);

    public T findById(String id) {
        Document doc = collection.find(Filters.eq("_id", toObjectId(id))).first();
        return doc == null ? null : fromDocument(remapId(doc));
    }

    public T findOne(Bson query) {
        Document doc = collection.find(query).first();
        return doc == null ? null : fromDocument(remapId(doc));
    }

    public List<T> findMany(Bson query) {
        List<T> out = new ArrayList<>();
        for (Document doc : collection.find(query)) {
            out.add(fromDocument(remapId(doc)));
        }
        return out;
    }

    public void insert(T entity) {
        collection.insertOne(toDocument(entity));
    }

    public void insertMany(List<T> entities) {
        List<Document> docs = new ArrayList<>();
        for (T e : entities) {
            docs.add(toDocument(e));
        }
        collection.insertMany(docs);
    }

    public void upsert(T entity) {
        String id = getId(entity);
        Document doc = toDocument(entity);
        collection.replaceOne(Filters.eq("_id", toObjectId(id)), doc, new ReplaceOptions().upsert(true));
    }

    public void delete(Bson filter) {
        collection.deleteMany(filter);
    }

    private static Document remapId(Document doc) {
        Object id = doc.get("_id");
        if (id != null) {
            doc.put("id", id.toString());
        }
        return doc;
    }

    private static Object toObjectId(String id) {
        if (id != null && ObjectId.isValid(id)) {
            return new ObjectId(id);
        }
        return id;
    }
}
