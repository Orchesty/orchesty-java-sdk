package io.orchesty.sdk.webhook;

import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.storage.Repository;
import java.util.Map;

/**
 * Repository for {@link Webhook} documents.
 */
public class WebhookRepository extends Repository<Webhook> {

    public WebhookRepository(WorkerApiClient client) {
        super(client, Webhook.COLLECTION);
    }

    @Override
    protected Webhook fromDocument(Map<String, Object> doc) {
        return Webhook.fromDocument(doc);
    }
}
