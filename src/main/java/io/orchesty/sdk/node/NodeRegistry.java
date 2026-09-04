package io.orchesty.sdk.node;

import io.orchesty.sdk.application.ABasicApplication;
import io.orchesty.sdk.application.IApplication;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of the worker's custom nodes, connectors, batches and applications.
 * Also provides the data for the discovery {@code /list} endpoints.
 */
public class NodeRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(NodeRegistry.class);

    /** A single entry in a {@code /list} response: node name + optional application. */
    public record ListItem(String name, String app) {}

    private final Map<String, ICustomNode> customNodes = new LinkedHashMap<>();
    private final Map<String, IConnector> connectors = new LinkedHashMap<>();
    private final Map<String, IBatch> batches = new LinkedHashMap<>();
    private final Map<String, IApplication> applications = new LinkedHashMap<>();

    public NodeRegistry registerCustomNode(ICustomNode node) {
        customNodes.put(node.getName(), node);
        return this;
    }

    public NodeRegistry registerConnector(IConnector connector) {
        connectors.put(connector.getName(), connector);
        return this;
    }

    public NodeRegistry registerBatch(IBatch batch) {
        batches.put(batch.getName(), batch);
        return this;
    }

    public NodeRegistry registerApplication(IApplication application) {
        applications.put(application.getName(), application);
        warnIfAlwaysAuthorized(application);
        return this;
    }

    private static void warnIfAlwaysAuthorized(IApplication application) {
        if (!(application instanceof ABasicApplication basic)) {
            return;
        }
        try {
            if (!basic.hasRequiredAuthorizationField()) {
                LOG.warn(
                        "[registry] Application [{}] has no required authorization field, so it counts as"
                                + " authorized even with an empty authorization form.",
                        application.getName());
            }
        } catch (RuntimeException e) {
            LOG.debug("[registry] Cannot inspect forms of application [{}]: {}", application.getName(), e.getMessage());
        }
    }

    public ICustomNode getCustomNode(String name) {
        ICustomNode node = customNodes.get(name);
        if (node == null) {
            throw new IllegalArgumentException("[hbpf.custom-node." + name + "] not found");
        }
        return node;
    }

    public IConnector getConnector(String name) {
        IConnector connector = connectors.get(name);
        if (connector == null) {
            throw new IllegalArgumentException("[hbpf.connector." + name + "] not found");
        }
        return connector;
    }

    public IBatch getBatch(String name) {
        IBatch batch = batches.get(name);
        if (batch == null) {
            throw new IllegalArgumentException("[hbpf.batch." + name + "] not found");
        }
        return batch;
    }

    public IApplication getApplication(String name) {
        IApplication application = applications.get(name);
        if (application == null) {
            throw new IllegalArgumentException("[" + name + "] application not found");
        }
        return application;
    }

    public Map<String, IApplication> getApplications() {
        return applications;
    }

    /** All registered connectors (used by the runtime to inject shared services). */
    public java.util.Collection<IConnector> getConnectors() {
        return connectors.values();
    }

    /** All registered batch nodes (used by the runtime to inject shared services). */
    public java.util.Collection<IBatch> getBatches() {
        return batches.values();
    }

    /** All registered custom nodes (used by the runtime to inject shared services). */
    public java.util.Collection<ICustomNode> getCustomNodes() {
        return customNodes.values();
    }

    public List<ListItem> listCustomNodes() {
        List<ListItem> list = new ArrayList<>();
        customNodes.keySet().forEach(name -> list.add(new ListItem(name, null)));
        list.sort(Comparator.comparing(i -> i.name().toLowerCase()));
        return list;
    }

    public List<ListItem> listConnectors() {
        List<ListItem> list = new ArrayList<>();
        connectors.forEach((name, connector) -> {
            String app = connector.getApplicationName();
            list.add(new ListItem(name, app == null || app.isEmpty() ? null : app));
        });
        list.sort(Comparator.comparing(i -> i.name().toLowerCase()));
        return list;
    }

    public List<ListItem> listBatches() {
        List<ListItem> list = new ArrayList<>();
        batches.forEach((name, batch) -> {
            String app = batch.getApplicationName();
            list.add(new ListItem(name, app == null || app.isEmpty() ? null : app));
        });
        list.sort(Comparator.comparing(i -> i.name().toLowerCase()));
        return list;
    }
}
