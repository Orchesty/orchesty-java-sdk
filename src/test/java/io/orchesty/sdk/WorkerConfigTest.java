package io.orchesty.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link WorkerConfig} URL resolution from environment variables, the tunnel TLS flag,
 * the required crypt secret and the request body limit.
 */
class WorkerConfigTest {

    private static final String SECRET = "ThisIsNotSoSecret";

    @Test
    void dockerTenantUsesComposeServiceNames() {
        WorkerConfig config = fromEnv(Map.of("TENANT_ID", "docker"));

        assertEquals("http://backend", config.getBackendHost());
        assertEquals("http://starting-point:8080", config.getStartingPoint());
        assertEquals("http://worker-api:8080", config.getWorkerApiHost());
        assertEquals("tunnel-proxy:50051", config.getTunnelProxyUrl());
        assertFalse(config.isTunnelSecure());
    }

    @Test
    void localTenantDefaultsToLoopback() {
        WorkerConfig config = fromEnv(Map.of("TENANT_ID", "local"));

        assertEquals("http://127.0.0.1:8080", config.getBackendHost());
        assertEquals("http://127.0.0.1:82", config.getStartingPoint());
        assertEquals("http://127.0.0.1:8081", config.getWorkerApiHost());
        assertEquals("127.0.0.1:50051", config.getTunnelProxyUrl());
        assertFalse(config.isTunnelSecure());
    }

    @Test
    void localTenantUsesDevIp() {
        WorkerConfig config = fromEnv(Map.of("TENANT_ID", "local", "DEV_IP", "10.0.0.5"));

        assertEquals("http://10.0.0.5:8080", config.getBackendHost());
        assertEquals("http://10.0.0.5:82", config.getStartingPoint());
        assertEquals("http://10.0.0.5:8081", config.getWorkerApiHost());
        assertEquals("10.0.0.5:50051", config.getTunnelProxyUrl());
    }

    @Test
    void cloudTenantUsesDefaultRegionAndTls() {
        WorkerConfig config = fromEnv(Map.of("TENANT_ID", "acme"));

        assertEquals("https://api-acme.eu2.cloud.orchesty.io", config.getBackendHost());
        assertEquals("https://start-acme.eu2.cloud.orchesty.io", config.getStartingPoint());
        assertEquals("https://worker-acme.eu2.cloud.orchesty.io", config.getWorkerApiHost());
        assertEquals("proxy-acme.eu2.cloud.orchesty.io:443", config.getTunnelProxyUrl());
        assertTrue(config.isTunnelSecure());
    }

    @Test
    void cloudTenantUsesCloudRegion() {
        WorkerConfig config = fromEnv(Map.of("TENANT_ID", "acme", "CLOUD_REGION", "us1"));

        assertEquals("https://worker-acme.us1.cloud.orchesty.io", config.getWorkerApiHost());
        assertEquals("proxy-acme.us1.cloud.orchesty.io:443", config.getTunnelProxyUrl());
    }

    @Test
    void withoutTenantUsesPrimaryEnvNames() {
        WorkerConfig config = fromEnv(Map.of(
                "BACKEND_URL", "http://be",
                "STARTING_POINT_URL", "http://sp",
                "WORKER_API_HOST", "http://wa",
                "TUNNEL_PROXY_URL", "proxy:1"));

        assertEquals("http://be", config.getBackendHost());
        assertEquals("http://sp", config.getStartingPoint());
        assertEquals("http://wa", config.getWorkerApiHost());
        assertEquals("proxy:1", config.getTunnelProxyUrl());
        assertFalse(config.isTunnelSecure());
    }

    @Test
    void withoutTenantFallsBackToDsnNames() {
        WorkerConfig config = fromEnv(Map.of(
                "BACKEND_DSN", "http://be-dsn",
                "STARTING_POINT_DSN", "http://sp-dsn"));

        assertEquals("http://be-dsn", config.getBackendHost());
        assertEquals("http://sp-dsn", config.getStartingPoint());
    }

    @Test
    void withoutTenantKeepsLegacyJavaEnvNames() {
        WorkerConfig config = fromEnv(Map.of(
                "ORCHESTY_BACKEND", "http://legacy-be",
                "STARTING_POINT", "http://legacy-sp"));

        assertEquals("http://legacy-be", config.getBackendHost());
        assertEquals("http://legacy-sp", config.getStartingPoint());
    }

    @Test
    void backendHostIsLastFallbackForBothUrls() {
        WorkerConfig config = fromEnv(Map.of("BACKEND_HOST", "http://host"));

        assertEquals("http://host", config.getBackendHost());
        assertEquals("http://host", config.getStartingPoint());
    }

    @Test
    void primaryEnvNamesWinOverLegacyNames() {
        WorkerConfig config = fromEnv(Map.of(
                "BACKEND_URL", "http://primary-be",
                "ORCHESTY_BACKEND", "http://legacy-be"));

        assertEquals("http://primary-be", config.getBackendHost());
    }

    @Test
    void tenantOverridesExplicitUrls() {
        WorkerConfig config = fromEnv(Map.of(
                "TENANT_ID", "docker",
                "WORKER_API_HOST", "http://explicit:9999",
                "BACKEND_URL", "http://explicit-be"));

        assertEquals("http://worker-api:8080", config.getWorkerApiHost());
        assertEquals("http://backend", config.getBackendHost());
    }

    @Test
    void emptyTenantIsTreatedAsUnset() {
        WorkerConfig config = fromEnv(Map.of(
                "TENANT_ID", "",
                "WORKER_API_HOST", "http://wa"));

        assertEquals("http://wa", config.getWorkerApiHost());
        assertFalse(config.isTunnelSecure());
    }

    @Test
    void nothingSetKeepsBackwardCompatibleDefaults() {
        WorkerConfig config = fromEnv(Map.of());

        assertEquals("http://127.0.0.1:8080", config.getWorkerApiHost());
        assertEquals("", config.getBackendHost());
        assertEquals("", config.getStartingPoint());
        assertEquals("", config.getTunnelProxyUrl());
        assertEquals(8080, config.getAppPort());
        assertFalse(config.isTunnelEnabled());
        assertFalse(config.isTunnelSecure());
    }

    @Test
    void tunnelSecureMatrix() {
        assertFalse(WorkerConfig.resolveTunnelSecure(Map.of()));
        assertFalse(WorkerConfig.resolveTunnelSecure(Map.of("TENANT_ID", "")));
        assertFalse(WorkerConfig.resolveTunnelSecure(Map.of("TENANT_ID", "local")));
        assertFalse(WorkerConfig.resolveTunnelSecure(Map.of("TENANT_ID", "docker")));
        assertTrue(WorkerConfig.resolveTunnelSecure(Map.of("TENANT_ID", "acme")));
    }

    @Test
    void builderDefaultsTunnelSecureToFalse() {
        assertFalse(WorkerConfig.builder().cryptSecret(SECRET).build().isTunnelSecure());
        assertTrue(WorkerConfig.builder()
                .cryptSecret(SECRET)
                .tunnelSecure(true)
                .build()
                .isTunnelSecure());
    }

    @Test
    void missingCryptSecretFailsFast() {
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> WorkerConfig.fromEnv(Map.of()));
        assertEquals("Env [CRYPT_SECRET] is missing.", missing.getMessage());

        IllegalStateException empty =
                assertThrows(IllegalStateException.class, () -> WorkerConfig.fromEnv(Map.of("CRYPT_SECRET", "")));
        assertEquals("Env [CRYPT_SECRET] is missing.", empty.getMessage());
    }

    @Test
    void builderRejectsMissingOrEmptyCryptSecret() {
        assertThrows(IllegalStateException.class, () -> WorkerConfig.builder().build());
        assertThrows(
                IllegalStateException.class,
                () -> WorkerConfig.builder().cryptSecret("").build());
    }

    @Test
    void cryptSecretIsReadFromEnv() {
        assertEquals(SECRET, fromEnv(Map.of()).getCryptSecret());
    }

    @Test
    void maxRequestBodyDefaultsToFiftyMegabytes() {
        WorkerConfig config = fromEnv(Map.of());

        assertEquals(50, config.getMaxRequestBodyMb());
        assertEquals(50L * 1024 * 1024, config.getMaxRequestBodyBytes());
        assertEquals(2, fromEnv(Map.of("MAX_REQUEST_BODY_MB", "2")).getMaxRequestBodyMb());
    }

    @Test
    void builderRejectsNonPositiveMaxRequestBody() {
        assertThrows(
                IllegalStateException.class,
                () -> WorkerConfig.builder()
                        .cryptSecret(SECRET)
                        .maxRequestBodyMb(0)
                        .build());
    }

    private static WorkerConfig fromEnv(Map<String, String> env) {
        Map<String, String> withSecret = new HashMap<>(env);
        withSecret.putIfAbsent("CRYPT_SECRET", SECRET);
        return WorkerConfig.fromEnv(withSecret);
    }
}
