package io.orchesty.sdk.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.Javalin;
import io.orchesty.sdk.node.ABatch;
import io.orchesty.sdk.node.AuditCheckpointNode;
import io.orchesty.sdk.node.IAuditCheckpoint;
import io.orchesty.sdk.node.ICustomNode;
import io.orchesty.sdk.node.NodeRegistry;
import io.orchesty.sdk.process.BatchProcessDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerRoutesTest {

    private Javalin app;
    private int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        NodeRegistry registry = new NodeRegistry();
        registry.registerCustomNode(new ICustomNode() {
            @Override
            public String getName() {
                return "echo";
            }

            @Override
            public ProcessDto process(ProcessDto dto) {
                Map<String, Object> data = dto.getJsonData();
                data.put("seen", true);
                dto.setJsonData(data);
                dto.setSuccessProcess("ok");
                return dto;
            }
        });
        registry.registerCustomNode(new ICustomNode() {
            @Override
            public String getName() {
                return "boom";
            }

            @Override
            public ProcessDto process(ProcessDto dto) {
                throw new RuntimeException("kaboom");
            }
        });
        // A batch node that fans the trigger payload's "ids" out into one message per id.
        registry.registerBatch(new ABatch() {
            @Override
            public String getName() {
                return "fanout";
            }

            @Override
            public BatchProcessDto process(BatchProcessDto dto) {
                Object ids = dto.getJsonData().get("ids");
                if (ids instanceof List<?> list) {
                    list.forEach(dto::addItem);
                }
                dto.setSuccessProcess("ok");
                return dto;
            }
        });

        // An audit checkpoint node: pass-through payload, emits the audit-checkpoint header.
        registry.registerCustomNode(new AuditCheckpointNode() {
            @Override
            public String getName() {
                return "checkpoint";
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
                        return List.of("id", "email");
                    }
                };
            }
        });

        app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            WorkerRoutes.register(cfg, registry);
        });
        app.start(0);
        port = app.port();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void processReturnsBridgeEnvelopeWithSuccessHeaders() throws Exception {
        String requestBody = "{\"body\":\"{\\\"a\\\":1}\",\"headers\":{\"node-id\":\"n1\"}}";
        HttpResponse<String> res = post("/custom-node/echo/process", requestBody);

        assertEquals(200, res.statusCode());
        Map<String, Object> envelope = parse(res.body());

        assertEquals("{\"a\":1,\"seen\":true}", envelope.get("body"));
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) envelope.get("headers");
        assertEquals(Integer.toString(ResultCode.SUCCESS.code()), headers.get(Headers.RESULT_CODE));
        assertEquals("ok", headers.get(Headers.RESULT_MESSAGE));
    }

    @Test
    void processFailureReturns400WithStopAndFailed() throws Exception {
        HttpResponse<String> res = post("/custom-node/boom/process", "{\"body\":\"{}\",\"headers\":{}}");

        assertEquals(400, res.statusCode());
        Map<String, Object> envelope = parse(res.body());
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) envelope.get("headers");
        assertEquals(Integer.toString(ResultCode.STOP_AND_FAILED.code()), headers.get(Headers.RESULT_CODE));
        assertTrue(((String) headers.get(Headers.RESULT_MESSAGE)).contains("kaboom"));
    }

    @Test
    void listReturnsRegisteredCustomNodes() throws Exception {
        HttpResponse<String> res = get("/custom-node/list");
        assertEquals(200, res.statusCode());
        List<Map<String, Object>> list = Json.fromString(res.body(), new TypeReference<>() {});
        List<String> names = list.stream().map(m -> (String) m.get("name")).toList();
        assertTrue(names.contains("echo"));
        assertTrue(names.contains("boom"));
    }

    @Test
    void testEndpointReturnsEmptyArray() throws Exception {
        HttpResponse<String> res = get("/custom-node/echo/process/test");
        assertEquals(200, res.statusCode());
        assertEquals("[]", res.body());
    }

    @Test
    void batchActionReturnsMessageArrayEnvelope() throws Exception {
        String requestBody = "{\"body\":\"{\\\"ids\\\":[1,2,3]}\",\"headers\":{\"node-id\":\"n1\"}}";
        HttpResponse<String> res = post("/batch/fanout/action", requestBody);

        assertEquals(200, res.statusCode());
        Map<String, Object> envelope = parse(res.body());

        // Batch envelope body is a JSON array string of {body, headers} messages.
        List<Map<String, Object>> messages = Json.fromString((String) envelope.get("body"), new TypeReference<>() {});
        assertEquals(3, messages.size());
        assertEquals("1", messages.get(0).get("body"));

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) envelope.get("headers");
        assertEquals(Integer.toString(ResultCode.SUCCESS.code()), headers.get(Headers.RESULT_CODE));
    }

    @Test
    void batchListReturnsRegisteredBatches() throws Exception {
        HttpResponse<String> res = get("/batch/list");
        assertEquals(200, res.statusCode());
        List<Map<String, Object>> list = Json.fromString(res.body(), new TypeReference<>() {});
        List<String> names = list.stream().map(m -> (String) m.get("name")).toList();
        assertTrue(names.contains("fanout"));
    }

    @Test
    void auditCheckpointNodeEmitsAuditCheckpointHeader() throws Exception {
        HttpResponse<String> res =
                post("/custom-node/checkpoint/process", "{\"body\":\"{\\\"id\\\":7}\",\"headers\":{}}");

        assertEquals(200, res.statusCode());
        Map<String, Object> envelope = parse(res.body());
        // Pass-through node: body is unchanged.
        assertEquals("{\"id\":7}", envelope.get("body"));

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) envelope.get("headers");
        String checkpoint = (String) headers.get(Headers.AUDIT_CHECKPOINT);
        assertTrue(checkpoint != null && checkpoint.contains("process_entry"));
        assertTrue(checkpoint.contains("email"));
    }

    @Test
    void unknownNodeReturnsApiError() throws Exception {
        HttpResponse<String> res = post("/custom-node/missing/process", "{\"body\":\"{}\",\"headers\":{}}");
        // missing node is a business error path -> 400 envelope with STOP_AND_FAILED
        assertEquals(400, res.statusCode());
        Map<String, Object> envelope = parse(res.body());
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) envelope.get("headers");
        assertEquals(Integer.toString(ResultCode.STOP_AND_FAILED.code()), headers.get(Headers.RESULT_CODE));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> parse(String json) {
        return Json.fromString(json, new TypeReference<>() {});
    }
}
