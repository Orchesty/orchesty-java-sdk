package io.orchesty.sdk.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.javalin.Javalin;
import io.orchesty.sdk.WorkerConfig;
import io.orchesty.sdk.process.Json;
import io.orchesty.sdk.tunnel.grpc.Frame;
import io.orchesty.sdk.tunnel.grpc.TunnelServiceGrpc;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs {@link TunnelClient} against an in-process fake tunnel-proxy (gRPC) and a local HTTP server.
 */
class TunnelClientTest {

    private Javalin local;
    private Server proxy;
    private TunnelClient client;
    private final CountDownLatch registered = new CountDownLatch(1);
    private final BlockingQueue<Frame> replies = new LinkedBlockingQueue<>();
    private volatile StreamObserver<Frame> toWorker;

    @BeforeEach
    void setUp() throws Exception {
        local = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.routes.post("/slow", ctx -> {
                Thread.sleep(1000);
                ctx.result("{\"ok\":true}");
            });
            cfg.routes.post("/hang", ctx -> {
                Thread.sleep(5000);
                ctx.result("{}");
            });
        });
        local.start(0);

        proxy = NettyServerBuilder.forPort(0)
                .addService(new TunnelServiceGrpc.TunnelServiceImplBase() {
                    @Override
                    public StreamObserver<Frame> openTunnel(StreamObserver<Frame> responseObserver) {
                        toWorker = responseObserver;
                        return new StreamObserver<>() {
                            @Override
                            public void onNext(Frame frame) {
                                if (frame.getRequestId().isEmpty()) {
                                    registered.countDown();
                                } else {
                                    replies.add(frame);
                                }
                            }

                            @Override
                            public void onError(Throwable t) {}

                            @Override
                            public void onCompleted() {}
                        };
                    }
                })
                .build()
                .start();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.stop();
        }
        proxy.shutdownNow();
        local.stop();
    }

    @Test
    void framesAreHandledConcurrently() throws Exception {
        connect(Duration.ofSeconds(60));

        long start = System.nanoTime();
        send("r1", "slow");
        send("r2", "slow");
        Frame first = replies.poll(5, TimeUnit.SECONDS);
        Frame second = replies.poll(5, TimeUnit.SECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(200, first.getStatusCode());
        assertEquals(200, second.getStatusCode());
        assertTrue(elapsedMs < 1800, "two 1 s requests took " + elapsedMs + " ms");
    }

    @Test
    void hungLocalRequestTimesOutWithErrorEnvelope() throws Exception {
        connect(Duration.ofMillis(300));

        send("r1", "hang");
        Frame reply = replies.poll(3, TimeUnit.SECONDS);

        assertNotNull(reply, "no reply before the local server answered");
        assertEquals("r1", reply.getRequestId());
        assertEquals(500, reply.getStatusCode());
        assertEquals("1006", headers(reply).get("result-code"));
    }

    @Test
    void errorMessageWithSpecialCharactersIsValidJson() throws Exception {
        connect(Duration.ofSeconds(60));

        send("r1", "bad path\\with\nnewline \"quoted\"");
        Frame reply = replies.poll(3, TimeUnit.SECONDS);

        assertNotNull(reply);
        assertEquals(500, reply.getStatusCode());
        String message = headers(reply).get("result-message");
        assertTrue(message.contains("\\with\nnewline \"quoted\""), message);
    }

    @Test
    void errorEnvelopeEscapesTheMessage() {
        Map<String, Object> envelope = Json.fromString(
                TunnelClient.errorEnvelope("a\\b\n\"c\""), new TypeReference<HashMap<String, Object>>() {});

        assertEquals("", envelope.get("body"));
        assertEquals(Map.of("result-code", "1006", "result-message", "a\\b\n\"c\""), envelope.get("headers"));
    }

    private void connect(Duration requestTimeout) throws InterruptedException {
        WorkerConfig config = WorkerConfig.builder()
                .cryptSecret("ThisIsNotSoSecret")
                .tunnelProxyUrl("127.0.0.1:" + proxy.getPort())
                .tunnelWorkerId("test-worker")
                .build();
        client = new TunnelClient(config, local.port(), requestTimeout);
        client.start();
        assertTrue(registered.await(5, TimeUnit.SECONDS), "worker did not register");
    }

    private void send(String requestId, String method) {
        synchronized (this) {
            toWorker.onNext(Frame.newBuilder()
                    .setRequestId(requestId)
                    .setMethod(method)
                    .setHttpMethod("POST")
                    .setPayload(ByteString.copyFromUtf8("{}"))
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headers(Frame reply) {
        Map<String, Object> envelope =
                Json.fromString(reply.getPayload().toStringUtf8(), new TypeReference<HashMap<String, Object>>() {});
        return (Map<String, String>) envelope.get("headers");
    }
}
