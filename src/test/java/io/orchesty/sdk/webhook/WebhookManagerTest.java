package io.orchesty.sdk.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.javalin.Javalin;
import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.ApplicationInstallRepository;
import io.orchesty.sdk.application.ApplicationManager;
import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.application.WorkerApiClient;
import io.orchesty.sdk.application.form.FormStack;
import io.orchesty.sdk.crypt.CryptManager;
import io.orchesty.sdk.crypt.WindWalkerCrypt;
import io.orchesty.sdk.exception.OnStopAndFailException;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.transport.HttpMethods;
import io.orchesty.sdk.transport.HttpSender;
import io.orchesty.sdk.transport.RequestDto;
import io.orchesty.sdk.transport.ResponseDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests webhook subscription through {@link WebhookManager} against a stub provider API. */
class WebhookManagerTest {

    private Javalin provider;
    private TestWebhookApp app;
    private final List<Webhook> inserted = new ArrayList<>();
    private WebhookManager manager;

    @BeforeEach
    void setUp() {
        provider = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.routes.post("/subscribe/ok", ctx -> ctx.result("{\"id\":\"w1\"}"));
            cfg.routes.post("/subscribe/denied", ctx -> ctx.status(401).result("{\"error\":\"unauthorized\"}"));
        });
        provider.start(0);

        app = new TestWebhookApp("http://127.0.0.1:" + provider.port());
        ApplicationInstall install =
                new ApplicationInstall().setUser("john").setName(app.getName()).setSdk("java");
        WorkerApiClient client = new WorkerApiClient("http://127.0.0.1:1", "");
        ApplicationInstallRepository installs =
                new ApplicationInstallRepository(client, new CryptManager(List.of(new WindWalkerCrypt("x")))) {
                    @Override
                    public ApplicationInstall findByNameAndUser(
                            String name, String user, List<String> sdks, Boolean enabled) {
                        return install;
                    }
                };
        WebhookRepository webhooks = new WebhookRepository(client) {
            @Override
            public void insert(Webhook entity) {
                inserted.add(entity);
            }
        };
        manager = new WebhookManager(
                Map.<String, IApplication>of(app.getName(), app), new HttpSender(), webhooks, installs, "http://sp");
    }

    @AfterEach
    void tearDown() {
        provider.stop();
    }

    @Test
    void successfulSubscriptionIsSaved() {
        List<Webhook> result = manager.subscribeWebhooks("hooks", "john", "java", body("ok"));

        assertEquals(1, result.size());
        assertEquals("w1", result.get(0).getWebhookId());
        assertEquals(1, inserted.size());
    }

    @Test
    void rejectedSubscriptionThrowsAndSavesNothing() {
        assertThrows(
                OnStopAndFailException.class, () -> manager.subscribeWebhooks("hooks", "john", "java", body("denied")));
        assertEquals(0, inserted.size());
    }

    @Test
    void requestParametersDoNotChangeTheSharedSubscription() {
        manager.subscribeWebhooks("hooks", "john", "java", body("ok"));

        assertEquals(Map.of("endpoint", "ok", "a", "1"), app.lastParameters);
        assertEquals(Map.of("a", "1"), app.subscription.getParameters());
    }

    @Test
    void syncListContainsWebhookActions() {
        ApplicationManager applications = new ApplicationManager(Map.of(app.getName(), app), null);

        assertEquals(AWebhookApplication.SYNC_ACTIONS, applications.getSynchronousActions("hooks"));
    }

    private static Map<String, Object> body(String endpoint) {
        return Map.of("name", "issue", "topology", "topo", "node", "start", "parameters", Map.of("endpoint", endpoint));
    }

    private static final class TestWebhookApp extends AWebhookApplication {

        private final String providerUrl;
        private final WebhookSubscription subscription =
                new WebhookSubscription("issue", "", "", new LinkedHashMap<>(Map.of("a", "1")), "");
        private Map<String, String> lastParameters;

        TestWebhookApp(String providerUrl) {
            this.providerUrl = providerUrl;
        }

        @Override
        public String getName() {
            return "hooks";
        }

        @Override
        public String getPublicName() {
            return "Hooks";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public String getAuthorizationType() {
            return "basic";
        }

        @Override
        public FormStack getFormStack() {
            return new FormStack();
        }

        @Override
        public boolean isAuthorized(ApplicationInstall applicationInstall) {
            return true;
        }

        @Override
        public List<WebhookSubscription> getWebhookSubscriptions() {
            return List.of(subscription);
        }

        @Override
        public RequestDto getWebhookSubscribeRequestDto(
                ApplicationInstall applicationInstall, WebhookSubscription subscription, String url) {
            lastParameters = Map.copyOf(subscription.getParameters());
            return new RequestDto(
                    providerUrl + "/subscribe/" + subscription.getParameters().get("endpoint"),
                    HttpMethods.POST,
                    new ProcessDto(),
                    "{}");
        }

        @Override
        public RequestDto getWebhookUnsubscribeRequestDto(ApplicationInstall applicationInstall, Webhook webhook) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String processWebhookSubscribeResponse(ResponseDto dto, ApplicationInstall applicationInstall) {
            return String.valueOf(dto.getJsonBody().get("id"));
        }

        @Override
        public boolean processWebhookUnsubscribeResponse(ResponseDto dto) {
            return true;
        }
    }
}
