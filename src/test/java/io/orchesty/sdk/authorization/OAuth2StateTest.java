package io.orchesty.sdk.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.ApplicationInstallRepository;
import io.orchesty.sdk.application.ApplicationManager;
import io.orchesty.sdk.application.ApplicationRoutes;
import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.application.form.Form;
import io.orchesty.sdk.application.form.FormStack;
import io.orchesty.sdk.crypt.CryptManager;
import io.orchesty.sdk.crypt.WindWalkerCrypt;
import io.orchesty.sdk.transport.HttpSender;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests the OAuth2 state format and the one-time nonce checked by both callback routes. */
class OAuth2StateTest {

    private Javalin tokenEndpoint;
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private InMemoryInstalls repository;
    private ApplicationManager manager;
    private ApplicationInstall install;

    @BeforeEach
    void setUp() {
        tokenEndpoint = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.routes.post("/token", ctx -> {
                tokenRequests.incrementAndGet();
                ctx.contentType("application/json").result("{\"access_token\":\"at\",\"token_type\":\"Bearer\"}");
            });
        });
        tokenEndpoint.start(0);

        TestOAuthApp app = new TestOAuthApp(
                new OAuth2Provider("http://backend", new HttpSender()),
                "http://127.0.0.1:" + tokenEndpoint.port() + "/token");
        install =
                new ApplicationInstall().setUser("john").setName(app.getName()).setSdk("java");
        install.addSettings(Map.of(CoreForm.AUTHORIZATION_FORM, Map.of("client_id", "id", "client_secret", "secret")));
        repository = new InMemoryInstalls(install);
        manager = new ApplicationManager(Map.<String, IApplication>of(app.getName(), app), repository);
    }

    @AfterEach
    void tearDown() {
        tokenEndpoint.stop();
    }

    @Test
    void stateRoundTripsWithAndWithoutNonce() {
        OAuth2Dto dto = new OAuth2Dto(new ApplicationInstall(), "", "");
        dto.setCustomAppDependencies("john", "app", "java");
        assertEquals(
                new OAuth2Provider.State("john", "app", "java", ""),
                OAuth2Provider.stateDecode(OAuth2Provider.stateEncode(dto)));

        dto.setStateNonce("n0nce");
        assertEquals(
                new OAuth2Provider.State("john", "app", "java", "n0nce"),
                OAuth2Provider.stateDecode(OAuth2Provider.stateEncode(dto)));
    }

    @Test
    void authorizeStoresNonceAndSendsItInState() {
        String url = manager.authorizationApplication("test-oauth", "john", "java", "http://ui/done");

        OAuth2Provider.State state = OAuth2Provider.stateDecode(stateParam(url));
        assertEquals("john", state.user());
        assertEquals("test-oauth", state.name());
        assertEquals("java", state.sdk());
        assertEquals(22, state.nonce().length());
        assertEquals(state.nonce(), authForm(repository.stored).get(OAuth2StateNonce.KEY));
    }

    @Test
    void callbackWithMatchingNonceStoresTokenOnce() {
        OAuth2Provider.State state = OAuth2Provider.stateDecode(
                stateParam(manager.authorizationApplication("test-oauth", "john", "java", "http://ui/done")));

        assertEquals("http://ui/done", manager.saveAuthorizationToken(state, Map.of("code", "c")));
        assertEquals(1, tokenRequests.get());
        assertFalse(authForm(repository.stored).containsKey(OAuth2StateNonce.KEY));
        assertTrue(authForm(repository.stored).containsKey("token"));

        assertThrows(IllegalStateException.class, () -> manager.saveAuthorizationToken(state, Map.of("code", "c")));
        assertEquals(1, tokenRequests.get());
    }

    @Test
    void callbackWithWrongOrMissingNonceIsRejected() {
        manager.authorizationApplication("test-oauth", "john", "java", "http://ui/done");

        assertThrows(
                IllegalStateException.class,
                () -> manager.saveAuthorizationToken(
                        new OAuth2Provider.State("john", "test-oauth", "java", "forged"), Map.of("code", "c")));
        assertThrows(
                IllegalStateException.class,
                () -> manager.saveAuthorizationToken(
                        new OAuth2Provider.State("john", "test-oauth", "java", ""), Map.of("code", "c")));
        assertEquals(0, tokenRequests.get());
        assertNull(authForm(repository.stored).get("token"));
    }

    @Test
    void perInstallCallbackWithMatchingStateStoresTokenOnce() throws Exception {
        String state = stateParam(manager.authorizationApplication("test-oauth", "john", "java", "http://ui/done"));

        HttpResponse<String> first = callback("/applications/test-oauth/users/john/sdk/java/authorize/token", state);
        HttpResponse<String> replay = callback("/applications/test-oauth/users/john/sdk/java/authorize/token", state);

        assertEquals(200, first.statusCode(), first.body());
        assertEquals("{\"redirectUrl\":\"http://ui/done\"}", first.body());
        assertEquals(400, replay.statusCode());
        assertEquals(1, tokenRequests.get());
    }

    @Test
    void perInstallCallbackForAnotherInstallIsRejected() throws Exception {
        String state = stateParam(manager.authorizationApplication("test-oauth", "john", "java", "http://ui/done"));

        HttpResponse<String> res = callback("/applications/test-oauth/users/orchesty/sdk/java/authorize/token", state);

        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("Invalid OAuth2 state."), res.body());
        assertEquals(0, tokenRequests.get());
        assertTrue(authForm(repository.stored).containsKey(OAuth2StateNonce.KEY));
    }

    @Test
    void perInstallCallbackWithoutStateIsRejected() throws Exception {
        manager.authorizationApplication("test-oauth", "john", "java", "http://ui/done");

        HttpResponse<String> res = callback("/applications/test-oauth/users/john/sdk/java/authorize/token", null);

        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("Missing \\\"state\\\" query parameter."), res.body());
        assertEquals(0, tokenRequests.get());
    }

    @Test
    void globalCallbackRouteStoresToken() throws Exception {
        String state = stateParam(manager.authorizationApplication("test-oauth", "john", "java", "http://ui/done"));

        HttpResponse<String> res = callback("/applications/authorize/token", state);

        assertEquals(200, res.statusCode(), res.body());
        assertEquals(1, tokenRequests.get());
    }

    /** Calls a callback route of a worker that serves {@link ApplicationRoutes} for {@link #manager}. */
    private HttpResponse<String> callback(String path, String state) throws Exception {
        Javalin worker = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            ApplicationRoutes.register(cfg, manager);
        });
        worker.start(0);
        try {
            String query =
                    "?code=c" + (state == null ? "" : "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + worker.port() + path + query))
                    .GET()
                    .build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } finally {
            worker.stop();
        }
    }

    private static String stateParam(String url) {
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            if (pair.startsWith("state=")) {
                return URLDecoder.decode(pair.substring("state=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("No state in " + url);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> authForm(ApplicationInstall install) {
        return (Map<String, Object>) install.getSettings().get(CoreForm.AUTHORIZATION_FORM);
    }

    /** Keeps a single install in memory instead of calling worker-api. */
    private static final class InMemoryInstalls extends ApplicationInstallRepository {

        private ApplicationInstall stored;

        InMemoryInstalls(ApplicationInstall install) {
            super(new WorkerApiClient("http://127.0.0.1:1", ""), new CryptManager(List.of(new WindWalkerCrypt("x"))));
            this.stored = install;
        }

        @Override
        public ApplicationInstall findByNameAndUser(String name, String user, List<String> sdks, Boolean enabled) {
            return stored;
        }

        @Override
        public void update(ApplicationInstall entity) {
            stored = entity;
        }
    }

    private static final class TestOAuthApp extends AOAuth2Application {

        private final String tokenUrl;

        TestOAuthApp(OAuth2Provider provider, String tokenUrl) {
            super(provider);
            this.tokenUrl = tokenUrl;
        }

        @Override
        public String getName() {
            return "test-oauth";
        }

        @Override
        public String getPublicName() {
            return "Test OAuth";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public FormStack getFormStack() {
            return new FormStack().addForm(new Form(CoreForm.AUTHORIZATION_FORM, "Authorization"));
        }

        @Override
        public String getAuthUrl() {
            return "https://provider/authorize";
        }

        @Override
        public String getTokenUrl() {
            return tokenUrl;
        }

        @Override
        public List<String> getScopes(ApplicationInstall applicationInstall) {
            return List.of("read");
        }
    }
}
