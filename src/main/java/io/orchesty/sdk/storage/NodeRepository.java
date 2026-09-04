package io.orchesty.sdk.storage;

import io.orchesty.sdk.application.WorkerApiClient;
import java.util.Map;

/**
 * Repository for {@link Node} documents.
 * Used by the error handler to read per-node repeater configuration.
 */
public class NodeRepository extends Repository<Node> {

    public NodeRepository(WorkerApiClient client) {
        super(client, Node.COLLECTION);
    }

    @Override
    protected Node fromDocument(Map<String, Object> doc) {
        return Node.fromDocument(doc);
    }
}
