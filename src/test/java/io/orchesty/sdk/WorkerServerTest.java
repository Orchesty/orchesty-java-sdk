package io.orchesty.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.orchesty.sdk.application.ABasicApplication;
import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.application.form.Field;
import io.orchesty.sdk.application.form.FieldType;
import io.orchesty.sdk.application.form.Form;
import io.orchesty.sdk.application.form.FormStack;
import io.orchesty.sdk.exception.OnRepeatException;
import io.orchesty.sdk.exception.OnStopAndFailException;
import io.orchesty.sdk.node.ACommonNode;
import io.orchesty.sdk.node.AConnector;
import io.orchesty.sdk.node.AuditCheckpointNode;
import io.orchesty.sdk.node.IAuditCheckpoint;
import io.orchesty.sdk.node.NodeRegistry;
import io.orchesty.sdk.process.Headers;
import io.orchesty.sdk.process.Json;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.process.ResultCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Boots a {@link WorkerServer} and checks request limits, late registration and error envelopes. */
class WorkerServerTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private WorkerServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void bodyOfTwoMegabytesIsProcessed() throws Exception {
        start(config(50), registryWith(new Echo()));

        String payload = "x".repeat(2 * 1024 * 1024);
        HttpResponse<String> res = post("/custom-node/echo/process", envelope(Map.of("payload", payload), Map.of()));

        assertEquals(200, res.statusCode());
        assertEquals(
                payload,
                Json.fromString(body(res), new TypeReference<Map<String, Object>>() {})
                        .get("payload"));
    }

    @Test
    void bodyOverTheLimitReturnsSdkEnvelope() throws Exception {
        start(config(1), registryWith(new Echo()));

        HttpResponse<String> res =
                post("/custom-node/echo/process", envelope(Map.of("payload", "x".repeat(2 * 1024 * 1024)), Map.of()));

        assertEquals(400, res.statusCode());
        assertEquals(
                Integer.toString(ResultCode.STOP_AND_FAILED.code()),
                headers(res).get(Headers.RESULT_CODE));
    }

    @Test
    void nodeRegisteredAfterConstructionGetsServicesAtStart() throws Exception {
        NodeRegistry registry = new NodeRegistry();
        server = new WorkerServer(config(50), registry);
        registry.registerCustomNode(new SenderProbe());
        server.start();

        HttpResponse<String> res = post("/custom-node/sender-probe/process", envelope(Map.of(), Map.of()));

        assertEquals(200, res.statusCode(), res.body());
        assertEquals("ok", headers(res).get(Headers.RESULT_MESSAGE));
    }

    @Test
    void connectorWithoutUserHeaderFailsWithUserNotDefined() throws Exception {
        // N8: setApplication returns AConnector, so the chain registers as a connector.
        AConnector connector = new SettingsReader().setApplication(new TestApp());
        NodeRegistry registry = new NodeRegistry().registerConnector(connector);
        start(config(50), registry);

        HttpResponse<String> res = post("/connector/settings-reader/action", envelope(Map.of(), Map.of()));

        assertEquals(400, res.statusCode());
        assertTrue(headers(res).get(Headers.RESULT_MESSAGE).contains("User not defined"), res.body());
    }

    @Test
    void auditCheckpointHeaderIsAddedToErrorResponses() throws Exception {
        NodeRegistry registry = new NodeRegistry()
                .registerCustomNode(new FailingCheckpoint("repeat", new OnRepeatException(60, 3, "later")))
                .registerCustomNode(new FailingCheckpoint("stop", new OnStopAndFailException("stop")))
                .registerCustomNode(new FailingCheckpoint("generic", new IllegalStateException("boom")));
        start(config(50), registry);

        for (String name : List.of("repeat", "stop", "generic")) {
            HttpResponse<String> res = post("/custom-node/" + name + "/process", envelope(Map.of(), Map.of()));
            String checkpoint = headers(res).get(Headers.AUDIT_CHECKPOINT);
            assertTrue(checkpoint != null && checkpoint.contains("process_entry"), name + ": " + res.body());
        }
    }

    private void start(WorkerConfig config, NodeRegistry registry) {
        server = new WorkerServer(config, registry);
        server.start();
    }

    private static WorkerConfig config(int maxRequestBodyMb) {
        return WorkerConfig.builder()
                .appPort(0)
                .workerApiHost("http://127.0.0.1:1")
                .cryptSecret("ThisIsNotSoSecret")
                .maxRequestBodyMb(maxRequestBodyMb)
                .build();
    }

    private static NodeRegistry registryWith(ACommonNode node) {
        return new NodeRegistry().registerCustomNode(node);
    }

    private static String envelope(Map<String, Object> body, Map<String, Object> headers) {
        return Json.toString(Map.of("body", Json.toString(body), "headers", headers));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.app().port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String body(HttpResponse<String> res) {
        return (String) Json.fromString(res.body(), new TypeReference<Map<String, Object>>() {})
                .get("body");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headers(HttpResponse<String> res) {
        return (Map<String, String>) Json.fromString(res.body(), new TypeReference<Map<String, Object>>() {})
                .get("headers");
    }

    private static final class Echo extends ACommonNode {
        @Override
        public String getName() {
            return "echo";
        }

        @Override
        public ProcessDto process(ProcessDto dto) {
            return dto.setSuccessProcess("ok");
        }
    }

    /** Fails unless the runtime injected its HttpSender. */
    private static final class SenderProbe extends ACommonNode {
        @Override
        public String getName() {
            return "sender-probe";
        }

        @Override
        public ProcessDto process(ProcessDto dto) {
            getSender();
            return dto.setSuccessProcess("ok");
        }
    }

    private static final class SettingsReader extends AConnector {
        @Override
        public String getName() {
            return "settings-reader";
        }

        @Override
        public ProcessDto process(ProcessDto dto) {
            getApplicationInstallFromProcess(dto);
            return dto;
        }
    }

    private static final class FailingCheckpoint extends AuditCheckpointNode {
        private final String name;
        private final RuntimeException error;

        FailingCheckpoint(String name, RuntimeException error) {
            this.name = name;
            this.error = error;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ProcessDto process(ProcessDto dto) {
            throw error;
        }

        @Override
        public IAuditCheckpoint getAuditCheckpoint() {
            return new IAuditCheckpoint() {
                @Override
                public String getRole() {
                    return "process_entry";
                }

                @Override
                public List<String> getFields() {
                    return List.of("id");
                }
            };
        }
    }

    private static final class TestApp extends ABasicApplication {
        @Override
        public String getName() {
            return "test-app";
        }

        @Override
        public String getPublicName() {
            return "Test app";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public FormStack getFormStack() {
            return new FormStack()
                    .addForm(new Form(CoreForm.AUTHORIZATION_FORM, "Authorization")
                            .addField(new Field(FieldType.TEXT, USER, "User", null, true)));
        }
    }
}
