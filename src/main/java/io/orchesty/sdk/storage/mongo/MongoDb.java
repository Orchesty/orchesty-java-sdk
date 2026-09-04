package io.orchesty.sdk.storage.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

/**
 * Thin wrapper over a {@link MongoClient} bound to a single database.
 * Intended for workers that persist their own state directly in MongoDB; platform documents
 * go through the Worker-API {@code DatabaseClient} instead.
 *
 * <p>Requires the optional {@code org.mongodb:mongodb-driver-sync} dependency.
 */
public class MongoDb implements AutoCloseable {

    private final MongoClient client;
    private final String database;

    public MongoDb(String dsn, String database) {
        this.client = MongoClients.create(dsn);
        this.database = database;
    }

    public MongoCollection<Document> getCollection(String collection) {
        return client.getDatabase(database).getCollection(collection);
    }

    public MongoClient getClient() {
        return client;
    }

    @Override
    public void close() {
        client.close();
    }
}
