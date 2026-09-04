package io.orchesty.sdk.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookTest {

    @Test
    void documentRoundTripsThroughMap() {
        Webhook webhook = new Webhook()
                .setName("newIssue")
                .setUser("u1")
                .setSdk("sdk1")
                .setToken("tok")
                .setNode("start")
                .setTopology("topo")
                .setApplication("github")
                .setWebhookId("ext-42");
        webhook.setId("64abc");

        Map<String, Object> doc = webhook.toDocument();
        assertEquals("64abc", doc.get("_id"));
        assertEquals("newIssue", doc.get("name"));
        assertEquals("ext-42", doc.get("webhookId"));

        Webhook restored = Webhook.fromDocument(doc);
        assertEquals("64abc", restored.getId());
        assertEquals("newIssue", restored.getName());
        assertEquals("u1", restored.getUser());
        assertEquals("topo", restored.getTopology());
        assertEquals("start", restored.getNode());
        assertEquals("ext-42", restored.getWebhookId());
        assertFalse(restored.isUnsubscribeFailed());
    }

    @Test
    void subscriptionCatalogFactoryDefaults() {
        WebhookSubscription subs = WebhookSubscription.create("newIssue");
        assertEquals("newIssue", subs.getName());
        assertTrue(subs.getParameters().isEmpty());
        assertEquals("", subs.getTopology());
    }

    @Test
    void toArrayExposesPublicFields() {
        Webhook webhook = new Webhook().setName("n").setToken("t").setWebhookId("w");
        Map<String, Object> arr = webhook.toArray();
        assertEquals("n", arr.get("name"));
        assertEquals("t", arr.get("token"));
        assertEquals("w", arr.get("webhookId"));
        assertTrue(arr.containsKey("id"));
    }

    @Test
    void webhookUrlIsBuiltFromStartingPoint() {
        WebhookManager manager = new WebhookManager(Map.of(), null, null, null, "https://start.example.com/");
        assertEquals(
                "https://start.example.com/topologies/topo/nodes/start/token/abc/run",
                manager.getWebhookUrl("topo", "start", "abc"));
    }

    @Test
    void auditCheckpointSerializesToJson() {
        io.orchesty.sdk.node.IAuditCheckpoint checkpoint = new io.orchesty.sdk.node.IAuditCheckpoint() {
            @Override
            public String getRole() {
                return "process_exit";
            }

            @Override
            public List<String> getFields() {
                return List.of("id");
            }
        };
        String json = checkpoint.toJson();
        assertTrue(json.contains("process_exit"));
        assertTrue(json.contains("\"entityPath\":\"$\""));
        assertTrue(json.contains("\"fields\":[\"id\"]"));
    }
}
