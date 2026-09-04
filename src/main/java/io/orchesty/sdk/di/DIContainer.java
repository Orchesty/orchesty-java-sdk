package io.orchesty.sdk.di;

import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.node.ABatch;
import io.orchesty.sdk.node.ANode;
import io.orchesty.sdk.node.IBatch;
import io.orchesty.sdk.node.IConnector;
import io.orchesty.sdk.node.ICustomNode;
import io.orchesty.sdk.node.NodeRegistry;
import io.orchesty.sdk.storage.ADocument;
import io.orchesty.sdk.storage.DatabaseClient;
import io.orchesty.sdk.storage.Repository;
import io.orchesty.sdk.transport.HttpSender;
import io.orchesty.sdk.webhook.AWebhookApplication;
import io.orchesty.sdk.webhook.WebhookManager;

/**
 * Lightweight dependency-injection helper that registers nodes and applications in a
 * {@link NodeRegistry}. When the corresponding shared services are set, it injects them at
 * registration time:
 * <ul>
 *   <li>{@link ANode}/{@link ABatch} receive the {@link HttpSender} and {@link DatabaseClient};</li>
 *   <li>connectors/batches also get their {@link IApplication} (2nd {@code setNode} arg);</li>
 *   <li>{@link AWebhookApplication} receives the {@link WebhookManager}.</li>
 * </ul>
 *
 * <p>{@link #setNode(Object)} dispatches on the concrete type so a
 * worker registers everything through one entry point. The composed {@link #getRegistry()}
 * is what you hand to {@code WorkerServer}. Repositories are looked up by document type via
 * the {@link DatabaseClient}.
 *
 * <p>Services are optional at construction: if a service is not set, its injection is simply
 * skipped. {@code WorkerServer} injects its services when it is constructed and again in
 * {@code start()}, so nodes and applications registered before {@code start()} are wired.
 */
public class DIContainer {

    private final NodeRegistry registry;
    private HttpSender httpSender;
    private DatabaseClient databaseClient;
    private WebhookManager webhookManager;

    public DIContainer() {
        this(new NodeRegistry());
    }

    public DIContainer(NodeRegistry registry) {
        this.registry = registry;
    }

    public DIContainer setHttpSender(HttpSender httpSender) {
        this.httpSender = httpSender;
        return this;
    }

    public DIContainer setDatabaseClient(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        return this;
    }

    public DIContainer setWebhookManager(WebhookManager webhookManager) {
        this.webhookManager = webhookManager;
        return this;
    }

    public NodeRegistry getRegistry() {
        return registry;
    }

    /**
     * Registers any node, dispatching by concrete type and injecting shared services.
     *
     * @param node connector, batch or custom node
     * @return this container
     */
    public DIContainer setNode(Object node) {
        return setNode(node, null);
    }

    /**
     * Registers a node and binds an application to it (connectors/batches).
     *
     * @param node connector, batch or custom node
     * @param application application to bind, or {@code null} for none
     * @return this container
     */
    public DIContainer setNode(Object node, IApplication application) {
        injectCommon(node, application);
        if (node instanceof IConnector connector) {
            registry.registerConnector(connector);
        } else if (node instanceof IBatch batch) {
            registry.registerBatch(batch);
        } else if (node instanceof ICustomNode customNode) {
            registry.registerCustomNode(customNode);
        } else {
            throw new IllegalArgumentException("Unsupported node type ["
                    + node.getClass().getName() + "]; expected IConnector, IBatch or ICustomNode.");
        }
        return this;
    }

    public DIContainer setConnector(IConnector connector) {
        injectCommon(connector, null);
        registry.registerConnector(connector);
        return this;
    }

    public DIContainer setBatch(IBatch batch) {
        injectCommon(batch, null);
        registry.registerBatch(batch);
        return this;
    }

    public DIContainer setCustomNode(ICustomNode customNode) {
        injectCommon(customNode, null);
        registry.registerCustomNode(customNode);
        return this;
    }

    public DIContainer setApplication(IApplication application) {
        if (application instanceof AWebhookApplication webhookApp && webhookManager != null) {
            webhookApp.setWebhookManager(webhookManager);
        }
        registry.registerApplication(application);
        return this;
    }

    public DatabaseClient databaseClient() {
        return databaseClient;
    }

    @SuppressWarnings("unchecked")
    public <T extends ADocument> Repository<T> getRepository(String collection) {
        if (databaseClient == null) {
            throw new IllegalStateException("DatabaseClient has not been set on the container.");
        }
        return (Repository<T>) databaseClient.getRepository(collection);
    }

    private void injectCommon(Object node, IApplication application) {
        if (node instanceof ANode aNode) {
            if (application != null) {
                aNode.setApplication(application);
            }
            if (httpSender != null) {
                aNode.setSender(httpSender);
            }
            if (databaseClient != null) {
                aNode.setDb(databaseClient);
            }
        } else if (node instanceof ABatch aBatch) {
            if (application != null) {
                aBatch.setApplication(application);
            }
            if (httpSender != null) {
                aBatch.setSender(httpSender);
            }
            if (databaseClient != null) {
                aBatch.setDb(databaseClient);
            }
        }
    }
}
