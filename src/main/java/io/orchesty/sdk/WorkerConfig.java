package io.orchesty.sdk;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker configuration resolved from environment variables.
 * Platform URLs are derived from a non-empty {@code TENANT_ID} ({@code local}, {@code docker}
 * or a cloud tenant), which takes precedence over explicit URL env vars; without it the
 * explicit env vars are used.
 */
public class WorkerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerConfig.class);

    private static final String DEFAULT_WORKER_API_HOST = "http://127.0.0.1:8080";

    /** Default request body limit in MB, the same as the tunnel-proxy's {@code MAX_REQUEST_BODY_MB}. */
    private static final int DEFAULT_MAX_REQUEST_BODY_MB = 50;

    /** Platform endpoints derivable from {@code TENANT_ID}. */
    enum Endpoint {
        BACKEND,
        STARTING_POINT,
        WORKER_API,
        TUNNEL_PROXY
    }

    private final int appPort;
    private final String cryptSecret;
    private final int maxRequestBodyMb;

    private final boolean tunnelEnabled;
    private final boolean tunnelSecure;
    private final String tunnelProxyUrl;
    private final String tunnelWorkerId;
    private final int tunnelReconnectIntervalMs;

    private final String workerApiHost;
    private final String orchestyApiKey;
    private final String backendHost;
    private final String startingPoint;

    private WorkerConfig(Builder b) {
        this.appPort = b.appPort;
        this.cryptSecret = b.cryptSecret;
        this.maxRequestBodyMb = b.maxRequestBodyMb;
        this.tunnelEnabled = b.tunnelEnabled;
        this.tunnelSecure = b.tunnelSecure;
        this.tunnelProxyUrl = b.tunnelProxyUrl;
        this.tunnelWorkerId = b.tunnelWorkerId;
        this.tunnelReconnectIntervalMs = b.tunnelReconnectIntervalMs;
        this.workerApiHost = b.workerApiHost;
        this.orchestyApiKey = b.orchestyApiKey;
        this.backendHost = b.backendHost;
        this.startingPoint = b.startingPoint;
    }

    public static WorkerConfig fromEnv() {
        return fromEnv(System.getenv());
    }

    public static WorkerConfig fromEnv(Map<String, String> env) {
        String workerApiHost = resolveUrl(env, Endpoint.WORKER_API, firstNonEmpty(env, "WORKER_API_HOST"));
        if (workerApiHost == null) {
            LOG.warn(
                    "[config] Neither TENANT_ID nor WORKER_API_HOST is set, falling back to {}",
                    DEFAULT_WORKER_API_HOST);
            workerApiHost = DEFAULT_WORKER_API_HOST;
        }

        String cryptSecret = firstNonEmpty(env, "CRYPT_SECRET");
        if (cryptSecret == null) {
            throw new IllegalStateException("Env [CRYPT_SECRET] is missing.");
        }

        return builder()
                .appPort(intEnv(env, "APP_PORT", 8080))
                .cryptSecret(cryptSecret)
                .maxRequestBodyMb(intEnv(env, "MAX_REQUEST_BODY_MB", DEFAULT_MAX_REQUEST_BODY_MB))
                .tunnelEnabled("true".equals(env(env, "TUNNEL_ENABLED", "false")))
                .tunnelSecure(resolveTunnelSecure(env))
                .tunnelProxyUrl(orEmpty(resolveUrl(env, Endpoint.TUNNEL_PROXY, firstNonEmpty(env, "TUNNEL_PROXY_URL"))))
                .tunnelWorkerId(env(env, "TUNNEL_WORKER_ID", ""))
                .tunnelReconnectIntervalMs(intEnv(env, "TUNNEL_RECONNECT_INTERVAL", 5000))
                .workerApiHost(workerApiHost)
                .orchestyApiKey(env(env, "ORCHESTY_API_KEY", ""))
                .backendHost(orEmpty(resolveUrl(
                        env,
                        Endpoint.BACKEND,
                        firstNonEmpty(env, "BACKEND_URL", "BACKEND_DSN", "ORCHESTY_BACKEND", "BACKEND_HOST"))))
                .startingPoint(orEmpty(resolveUrl(
                        env,
                        Endpoint.STARTING_POINT,
                        firstNonEmpty(
                                env, "STARTING_POINT_URL", "STARTING_POINT_DSN", "STARTING_POINT", "BACKEND_HOST"))))
                .build();
    }

    /**
     * Resolves the URL of a platform endpoint.
     * A non-empty {@code TENANT_ID} derives the URL ({@code local} → {@code DEV_IP} or 127.0.0.1,
     * {@code docker} → compose service names, anything else →
     * {@code *.{CLOUD_REGION|eu2}.cloud.orchesty.io}) and takes precedence over {@code fallback}.
     *
     * @param env environment variables
     * @param endpoint endpoint to resolve
     * @param fallback explicitly configured URL, may be {@code null}
     * @return the derived URL, or {@code fallback} when {@code TENANT_ID} is not set
     */
    static String resolveUrl(Map<String, String> env, Endpoint endpoint, String fallback) {
        String tenantId = firstNonEmpty(env, "TENANT_ID");
        if (tenantId == null) {
            return fallback;
        }

        String url;
        if ("local".equals(tenantId)) {
            String host = env(env, "DEV_IP", "127.0.0.1");
            url = switch (endpoint) {
                case BACKEND -> "http://" + host + ":8080";
                case STARTING_POINT -> "http://" + host + ":82";
                case WORKER_API -> "http://" + host + ":8081";
                case TUNNEL_PROXY -> host + ":50051";
            };
        } else if ("docker".equals(tenantId)) {
            url = switch (endpoint) {
                case BACKEND -> "http://backend";
                case STARTING_POINT -> "http://starting-point:8080";
                case WORKER_API -> "http://worker-api:8080";
                case TUNNEL_PROXY -> "tunnel-proxy:50051";
            };
        } else {
            String domain = env(env, "CLOUD_REGION", "eu2") + ".cloud.orchesty.io";
            url = switch (endpoint) {
                case BACKEND -> "https://api-" + tenantId + "." + domain;
                case STARTING_POINT -> "https://start-" + tenantId + "." + domain;
                case WORKER_API -> "https://worker-" + tenantId + "." + domain;
                case TUNNEL_PROXY -> "proxy-" + tenantId + "." + domain + ":443";
            };
        }

        if (fallback != null && !fallback.equals(url)) {
            LOG.info("[config] TENANT_ID={} overrides explicit {} URL {} with {}", tenantId, endpoint, fallback, url);
        }
        return url;
    }

    /**
     * Determines whether the tunnel uses TLS, which is the case for any tenant except
     * {@code local}/{@code docker}.
     *
     * @param env environment variables
     * @return {@code true} if the tunnel channel should use TLS
     */
    static boolean resolveTunnelSecure(Map<String, String> env) {
        String tenantId = firstNonEmpty(env, "TENANT_ID");
        return tenantId != null && !"local".equals(tenantId) && !"docker".equals(tenantId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getAppPort() {
        return appPort;
    }

    public String getCryptSecret() {
        return cryptSecret;
    }

    /** Maximum accepted request body size in MB ({@code MAX_REQUEST_BODY_MB}). */
    public int getMaxRequestBodyMb() {
        return maxRequestBodyMb;
    }

    /** Maximum accepted request body size in bytes. */
    public long getMaxRequestBodyBytes() {
        return maxRequestBodyMb * 1024L * 1024L;
    }

    public boolean isTunnelEnabled() {
        return tunnelEnabled;
    }

    /** Whether the tunnel channel uses TLS (cloud tenants) instead of plaintext. */
    public boolean isTunnelSecure() {
        return tunnelSecure;
    }

    public String getTunnelProxyUrl() {
        return tunnelProxyUrl;
    }

    public String getTunnelWorkerId() {
        return tunnelWorkerId;
    }

    public int getTunnelReconnectIntervalMs() {
        return tunnelReconnectIntervalMs;
    }

    public String getWorkerApiHost() {
        return workerApiHost;
    }

    public String getOrchestyApiKey() {
        return orchestyApiKey;
    }

    public String getBackendHost() {
        return backendHost;
    }

    /** Base URL used to build webhook callback (starting-point) URLs. */
    public String getStartingPoint() {
        return startingPoint;
    }

    private static String env(Map<String, String> env, String name, String def) {
        String v = env.get(name);
        return v == null || v.isEmpty() ? def : v;
    }

    private static int intEnv(Map<String, String> env, String name, int def) {
        String v = env.get(name);
        try {
            return v == null || v.isEmpty() ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Returns the value of the first environment variable that is set and non-empty.
     *
     * @param env environment variables
     * @param names variable names, checked in order
     * @return the first non-empty value, or {@code null} if none is set
     */
    private static String firstNonEmpty(Map<String, String> env, String... names) {
        for (String name : names) {
            String v = env.get(name);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }

    public static final class Builder {
        private int appPort = 8080;
        private String cryptSecret;
        private int maxRequestBodyMb = DEFAULT_MAX_REQUEST_BODY_MB;
        private boolean tunnelEnabled;
        private boolean tunnelSecure;
        private String tunnelProxyUrl = "";
        private String tunnelWorkerId = "";
        private int tunnelReconnectIntervalMs = 5000;
        private String workerApiHost = DEFAULT_WORKER_API_HOST;
        private String orchestyApiKey = "";
        private String backendHost = "";
        private String startingPoint = "";

        public Builder appPort(int v) {
            this.appPort = v;
            return this;
        }

        public Builder cryptSecret(String v) {
            this.cryptSecret = v;
            return this;
        }

        public Builder maxRequestBodyMb(int v) {
            this.maxRequestBodyMb = v;
            return this;
        }

        public Builder tunnelEnabled(boolean v) {
            this.tunnelEnabled = v;
            return this;
        }

        public Builder tunnelSecure(boolean v) {
            this.tunnelSecure = v;
            return this;
        }

        public Builder tunnelProxyUrl(String v) {
            this.tunnelProxyUrl = v;
            return this;
        }

        public Builder tunnelWorkerId(String v) {
            this.tunnelWorkerId = v;
            return this;
        }

        public Builder tunnelReconnectIntervalMs(int v) {
            this.tunnelReconnectIntervalMs = v;
            return this;
        }

        public Builder workerApiHost(String v) {
            this.workerApiHost = v;
            return this;
        }

        public Builder orchestyApiKey(String v) {
            this.orchestyApiKey = v;
            return this;
        }

        public Builder backendHost(String v) {
            this.backendHost = v;
            return this;
        }

        public Builder startingPoint(String v) {
            this.startingPoint = v;
            return this;
        }

        public WorkerConfig build() {
            if (cryptSecret == null || cryptSecret.isEmpty()) {
                throw new IllegalStateException("Crypt secret (CRYPT_SECRET) must not be empty.");
            }
            if (maxRequestBodyMb < 1) {
                throw new IllegalStateException("MAX_REQUEST_BODY_MB must be at least 1.");
            }
            return new WorkerConfig(this);
        }
    }
}
