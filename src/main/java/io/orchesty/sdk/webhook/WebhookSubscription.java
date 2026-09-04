package io.orchesty.sdk.webhook;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalog entry of a webhook event an application can subscribe to.
 * Subscriptions are keyed by {@code name}; the topology/node binding is supplied by
 * the UI (the {@code node}/{@code topology} args remain for backward compatibility).
 */
public class WebhookSubscription {

    private final String name;
    private final String node;
    private final String topology;
    private final Map<String, String> parameters;
    private final String description;

    public WebhookSubscription(
            String name, String node, String topology, Map<String, String> parameters, String description) {
        this.name = name;
        this.node = node;
        this.topology = topology;
        this.parameters = parameters == null ? new LinkedHashMap<>() : parameters;
        this.description = description == null ? "" : description;
    }

    /**
     * Creates a subscription from its name with optional parameters and description; this is the preferred factory.
     *
     * @param name subscription name
     * @param parameters subscription parameters, or {@code null} for none
     * @param description human-readable description, or {@code null} for none
     * @return the new subscription
     */
    public static WebhookSubscription create(String name, Map<String, String> parameters, String description) {
        return new WebhookSubscription(name, "", "", parameters, description);
    }

    public static WebhookSubscription create(String name) {
        return create(name, new LinkedHashMap<>(), "");
    }

    public String getName() {
        return name;
    }

    public String getNode() {
        return node;
    }

    public String getTopology() {
        return topology;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getDescription() {
        return description;
    }
}
