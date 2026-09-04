package io.orchesty.sdk.webhook;

import io.orchesty.sdk.storage.ADocument;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted webhook subscription document.
 * Collection name (Worker-API {@code /document/<collection>}): {@value #COLLECTION}.
 */
public class Webhook extends ADocument {

    public static final String COLLECTION = "Webhook";

    private String name = "";
    private String user = "";
    private String sdk = "";
    private String token = "";
    private String node = "";
    private String topology = "";
    private String application = "";
    private String webhookId = "";
    private boolean unsubscribeFailed;
    private boolean deleted;

    @Override
    public String getCollection() {
        return COLLECTION;
    }

    public String getName() {
        return name;
    }

    public Webhook setName(String name) {
        this.name = name;
        return this;
    }

    public String getUser() {
        return user;
    }

    public Webhook setUser(String user) {
        this.user = user;
        return this;
    }

    public String getSdk() {
        return sdk;
    }

    public Webhook setSdk(String sdk) {
        this.sdk = sdk;
        return this;
    }

    public String getToken() {
        return token;
    }

    public Webhook setToken(String token) {
        this.token = token;
        return this;
    }

    public String getNode() {
        return node;
    }

    public Webhook setNode(String node) {
        this.node = node;
        return this;
    }

    public String getTopology() {
        return topology;
    }

    public Webhook setTopology(String topology) {
        this.topology = topology;
        return this;
    }

    public String getApplication() {
        return application;
    }

    public Webhook setApplication(String application) {
        this.application = application;
        return this;
    }

    public String getWebhookId() {
        return webhookId;
    }

    public Webhook setWebhookId(String webhookId) {
        this.webhookId = webhookId;
        return this;
    }

    public boolean isUnsubscribeFailed() {
        return unsubscribeFailed;
    }

    public Webhook setUnsubscribeFailed(boolean unsubscribeFailed) {
        this.unsubscribeFailed = unsubscribeFailed;
        return this;
    }

    @Override
    public Map<String, Object> toDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", id);
        doc.put("name", name);
        doc.put("user", user);
        doc.put("sdk", sdk);
        doc.put("token", token);
        doc.put("node", node);
        doc.put("topology", topology);
        doc.put("application", application);
        doc.put("webhookId", webhookId);
        doc.put("unsubscribeFailed", unsubscribeFailed);
        doc.put("deleted", deleted);
        return doc;
    }

    public Map<String, Object> toArray() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", name);
        out.put("user", user);
        out.put("sdk", sdk);
        out.put("token", token);
        out.put("node", node);
        out.put("topology", topology);
        out.put("application", application);
        out.put("webhookId", webhookId);
        out.put("deleted", deleted);
        return out;
    }

    public static Webhook fromDocument(Map<String, Object> doc) {
        Webhook w = new Webhook();
        Object idVal = doc.getOrDefault("_id", doc.get("id"));
        w.id = idVal == null ? "" : String.valueOf(idVal);
        w.name = str(doc.get("name"));
        w.user = str(doc.get("user"));
        w.sdk = str(doc.get("sdk"));
        w.token = str(doc.get("token"));
        w.node = str(doc.get("node"));
        w.topology = str(doc.get("topology"));
        w.application = str(doc.get("application"));
        w.webhookId = str(doc.get("webhookId"));
        w.unsubscribeFailed = Boolean.TRUE.equals(doc.get("unsubscribeFailed"));
        w.deleted = Boolean.TRUE.equals(doc.get("deleted"));
        return w;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
