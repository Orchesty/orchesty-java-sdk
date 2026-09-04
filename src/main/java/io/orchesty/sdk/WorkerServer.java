package io.orchesty.sdk;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.orchesty.sdk.application.ApplicationInstallRepository;
import io.orchesty.sdk.application.ApplicationManager;
import io.orchesty.sdk.application.ApplicationRoutes;
import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.authorization.AOAuth2Application;
import io.orchesty.sdk.authorization.OAuth2Provider;
import io.orchesty.sdk.crypt.CryptManager;
import io.orchesty.sdk.crypt.WindWalkerCrypt;
import io.orchesty.sdk.http.WorkerRoutes;
import io.orchesty.sdk.metrics.Metrics;
import io.orchesty.sdk.metrics.MetricsHandler;
import io.orchesty.sdk.middleware.ErrorHandler;
import io.orchesty.sdk.node.ABatch;
import io.orchesty.sdk.node.ANode;
import io.orchesty.sdk.node.IBatch;
import io.orchesty.sdk.node.IConnector;
import io.orchesty.sdk.node.ICustomNode;
import io.orchesty.sdk.node.NodeRegistry;
import io.orchesty.sdk.process.Headers;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.storage.DatabaseClient;
import io.orchesty.sdk.storage.NodeRepository;
import io.orchesty.sdk.transport.HttpSender;
import io.orchesty.sdk.transport.RequestDto;
import io.orchesty.sdk.tunnel.TunnelClient;
import io.orchesty.sdk.webhook.AWebhookApplication;
import io.orchesty.sdk.webhook.WebhookManager;
import io.orchesty.sdk.webhook.WebhookRepository;
import io.orchesty.sdk.webhook.WebhookRoutes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker bootstrap that runs a Javalin HTTP server exposing the Orchesty worker contract,
 * reachable either directly over HTTP or via the gRPC tunnel-proxy.
 * When the tunnel is enabled the HTTP server binds to {@code 127.0.0.1:0} (random local port)
 * and the {@link TunnelClient} forwards proxied requests to it.
 */
public class WorkerServer {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerServer.class);

    private final WorkerConfig config;
    private final NodeRegistry registry;
    private final List<Consumer<JavalinConfig>> extraRoutes;

    private Javalin app;
    private TunnelClient tunnelClient;
    private final CryptManager cryptManager;
    private final WorkerApiClient workerApiClient;
    private final ApplicationInstallRepository applicationInstallRepository;
    private final NodeRepository nodeRepository;
    private final WebhookRepository webhookRepository;
    private final DatabaseClient databaseClient;
    private final HttpSender httpSender;
    private final OAuth2Provider oauth2Provider;
    private final Metrics metrics;
    private final io.orchesty.sdk.logger.Logger logger;
    private final ErrorHandler errorHandler;
    private final WebhookManager webhookManager;
    private final ApplicationManager applicationManager;

    public WorkerServer(WorkerConfig config, NodeRegistry registry) {
        this(config, registry, new ArrayList<>());
    }

    public WorkerServer(WorkerConfig config, NodeRegistry registry, List<Consumer<JavalinConfig>> extraRoutes) {
        this.config = config;
        this.registry = registry;
        this.extraRoutes = extraRoutes;

        this.cryptManager = new CryptManager(List.of(new WindWalkerCrypt(config.getCryptSecret())));
        this.workerApiClient = new WorkerApiClient(config.getWorkerApiHost(), config.getOrchestyApiKey());
        this.applicationInstallRepository = new ApplicationInstallRepository(workerApiClient, cryptManager);
        this.nodeRepository = new NodeRepository(workerApiClient);
        this.webhookRepository = new WebhookRepository(workerApiClient);
        this.databaseClient = new DatabaseClient(workerApiClient)
                .register(applicationInstallRepository)
                .register(nodeRepository)
                .register(webhookRepository);
        this.metrics = new Metrics(workerApiClient);
        this.logger = new io.orchesty.sdk.logger.Logger(workerApiClient);
        this.errorHandler = new ErrorHandler(nodeRepository);
        this.httpSender = new HttpSender().setMetricsRecorder(this::recordCurlMetrics);
        this.oauth2Provider = new OAuth2Provider(config.getBackendHost(), httpSender);
        this.webhookManager = new WebhookManager(
                registry.getApplications(),
                httpSender,
                webhookRepository,
                applicationInstallRepository,
                config.getStartingPoint());
        // Injected here and again in start(), so applications and nodes registered after the
        // constructor are wired too. Repeating the injection is safe.
        injectApplicationServices();
        injectNodeServices();
        this.applicationManager = new ApplicationManager(registry.getApplications(), applicationInstallRepository)
                .setWebhookManager(webhookManager);
    }

    /**
     * Gives webhook-aware applications the {@link WebhookManager} (needed by their sync actions)
     * and OAuth2 applications the {@link OAuth2Provider}.
     */
    private void injectApplicationServices() {
        for (IApplication application : registry.getApplications().values()) {
            if (application instanceof AWebhookApplication webhookApp) {
                webhookApp.setWebhookManager(webhookManager);
            }
            if (application instanceof AOAuth2Application oauthApp) {
                oauthApp.setProvider(oauth2Provider);
            }
        }
    }

    /** Gives every {@link ANode}/{@link ABatch} the shared {@link HttpSender} and {@link DatabaseClient}. */
    private void injectNodeServices() {
        for (IConnector connector : registry.getConnectors()) {
            if (connector instanceof ANode aNode) {
                aNode.setSender(httpSender).setDb(databaseClient);
            }
        }
        for (IBatch batch : registry.getBatches()) {
            if (batch instanceof ABatch aBatch) {
                aBatch.setSender(httpSender).setDb(databaseClient);
            }
        }
        for (ICustomNode node : registry.getCustomNodes()) {
            if (node instanceof ANode aNode) {
                aNode.setSender(httpSender).setDb(databaseClient);
            }
        }
    }

    /**
     * Records an {@link HttpSender} outcome as curl metrics using the request's {@code debugInfo}.
     *
     * @param request the sent request
     * @param responseCode HTTP status code of the response
     * @param durationMillis request duration in milliseconds
     * @param responseError error message, or {@code null} on success
     */
    private void recordCurlMetrics(RequestDto request, int responseCode, long durationMillis, String responseError) {
        Metrics.Times times = new Metrics.Times(durationMillis, 0, 0);
        ProcessDto dto = request.getDebugInfo();
        String user = dto == null ? null : dto.getHeader(Headers.USER);
        String nodeId = dto == null ? null : dto.getHeader(Headers.NODE_ID);
        String nodeName = dto == null ? null : dto.getHeader(Headers.NODE_NAME);
        String topologyId = dto == null ? null : dto.getHeader(Headers.TOPOLOGY_ID);
        String correlationId = dto == null ? null : dto.getHeader(Headers.CORRELATION_ID);
        String appKey = dto == null ? null : dto.getCurrentApp();
        metrics.sendCurlMetrics(
                times,
                responseCode,
                user,
                nodeId,
                nodeName,
                topologyId,
                appKey,
                correlationId,
                request.getUrl(),
                responseError);
    }

    /**
     * Registers additional routes; must be called before {@link #start()}.
     * Javalin 7 only accepts routes while the server is being configured, e.g.
     * {@code server.withRoutes(cfg -> cfg.routes.get("/hello", ...))}.
     *
     * @param registrar callback that registers routes on the Javalin configuration
     * @return this server
     */
    public WorkerServer withRoutes(Consumer<JavalinConfig> registrar) {
        extraRoutes.add(registrar);
        return this;
    }

    public void start() {
        injectApplicationServices();
        injectNodeServices();

        app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.http.maxRequestSize = config.getMaxRequestBodyBytes();
            cfg.requestLogger.http((ctx, ms) -> LOG.info(
                    "[worker] {} {} -> {} ({} ms)", ctx.method(), ctx.path(), ctx.statusCode(), Math.round(ms)));
            new MetricsHandler(metrics).register(cfg);
            WorkerRoutes.register(cfg, registry, errorHandler);
            ApplicationRoutes.register(cfg, applicationManager);
            WebhookRoutes.register(cfg, webhookManager);
            extraRoutes.forEach(r -> r.accept(cfg));
        });

        if (config.isTunnelEnabled()) {
            app.start("127.0.0.1", 0);
            int port = app.port();
            LOG.info("[tunnel] Internal HTTP server on 127.0.0.1:{}", port);
            tunnelClient = new TunnelClient(config, port);
            tunnelClient.start();
        } else {
            app.start(config.getAppPort());
            LOG.info("[server]: Server is running at http://localhost:{}", config.getAppPort());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "orchesty-shutdown"));
    }

    public void stop() {
        if (tunnelClient != null) {
            tunnelClient.stop();
        }
        if (app != null) {
            app.stop();
        }
    }

    public Javalin app() {
        return app;
    }

    public NodeRegistry registry() {
        return registry;
    }

    public WorkerConfig config() {
        return config;
    }

    public CryptManager cryptManager() {
        return cryptManager;
    }

    public ApplicationInstallRepository applicationInstallRepository() {
        return applicationInstallRepository;
    }

    public NodeRepository nodeRepository() {
        return nodeRepository;
    }

    public DatabaseClient databaseClient() {
        return databaseClient;
    }

    public HttpSender httpSender() {
        return httpSender;
    }

    public OAuth2Provider oauth2Provider() {
        return oauth2Provider;
    }

    public Metrics metrics() {
        return metrics;
    }

    public io.orchesty.sdk.logger.Logger logger() {
        return logger;
    }

    public WebhookRepository webhookRepository() {
        return webhookRepository;
    }

    public WebhookManager webhookManager() {
        return webhookManager;
    }

    public WorkerApiClient workerApiClient() {
        return workerApiClient;
    }

    public ApplicationManager applicationManager() {
        return applicationManager;
    }
}
