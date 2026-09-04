package io.orchesty.sdk.tunnel;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.orchesty.sdk.WorkerConfig;
import io.orchesty.sdk.process.Json;
import io.orchesty.sdk.tunnel.grpc.Frame;
import io.orchesty.sdk.tunnel.grpc.TunnelServiceGrpc;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC client that exposes this worker through the tunnel-proxy.
 * Opens a bidirectional {@code OpenTunnel} stream, registers this worker, forwards each incoming
 * request frame to the local Javalin server and writes the HTTP response back as a frame.
 * Frames are handled concurrently, each on its own virtual thread, so a slow request does not
 * block the others. Reconnects automatically with exponential backoff.
 */
public class TunnelClient {

    private static final Logger LOG = LoggerFactory.getLogger(TunnelClient.class);
    private static final long MAX_RECONNECT_INTERVAL_MS = 60_000;

    /** Local request timeout, the same as the Bridge's request timeout. */
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final WorkerConfig config;
    private final int localPort;
    private final Duration requestTimeout;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "orchesty-tunnel");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService frameExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    private volatile ManagedChannel channel;
    private volatile StreamObserver<Frame> requestStream;
    private volatile boolean stopping;

    public TunnelClient(WorkerConfig config, int localPort) {
        this(config, localPort, DEFAULT_REQUEST_TIMEOUT);
    }

    TunnelClient(WorkerConfig config, int localPort, Duration requestTimeout) {
        this.config = config;
        this.localPort = localPort;
        this.requestTimeout = requestTimeout;
    }

    public void start() {
        if (config.getTunnelProxyUrl().isEmpty()) {
            throw new IllegalStateException("TUNNEL_PROXY_URL is required when TUNNEL_ENABLED=true");
        }
        if (config.getTunnelWorkerId().isEmpty()) {
            throw new IllegalStateException("TUNNEL_WORKER_ID is required when TUNNEL_ENABLED=true");
        }
        connect();
    }

    public void stop() {
        stopping = true;
        StreamObserver<Frame> stream = this.requestStream;
        if (stream != null) {
            synchronized (this) {
                try {
                    stream.onCompleted();
                } catch (RuntimeException ignored) {
                    // already closed
                }
            }
        }
        ManagedChannel ch = this.channel;
        if (ch != null) {
            ch.shutdownNow();
        }
        scheduler.shutdownNow();
        frameExecutor.shutdownNow();
        LOG.info("[tunnel] Shutdown complete.");
    }

    private void connect() {
        if (stopping) {
            return;
        }
        String target = normalizeTarget(config.getTunnelProxyUrl());
        LOG.info(
                "[tunnel] Connecting to {} as \"{}\" ({})...",
                target,
                config.getTunnelWorkerId(),
                config.isTunnelSecure() ? "TLS" : "plaintext");

        NettyChannelBuilder builder = NettyChannelBuilder.forTarget(target);
        if (config.isTunnelSecure()) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }
        channel = builder.keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                // Accept every request the proxy accepts (its body limit) plus room for the frame.
                .maxInboundMessageSize((int) Math.min(Integer.MAX_VALUE, config.getMaxRequestBodyBytes() + 1024 * 1024))
                .build();

        TunnelServiceGrpc.TunnelServiceStub stub = TunnelServiceGrpc.newStub(channel);
        StreamObserver<Frame> stream = stub.openTunnel(new StreamObserver<>() {
            @Override
            public void onNext(Frame frame) {
                if (reconnectAttempt.get() > 0) {
                    LOG.info("[tunnel] Connection confirmed by server.");
                    reconnectAttempt.set(0);
                }
                dispatch(frame);
            }

            @Override
            public void onError(Throwable t) {
                if (stopping) {
                    return;
                }
                LOG.warn("[tunnel] Stream error: {}", t.getMessage());
                scheduleReconnect();
            }

            @Override
            public void onCompleted() {
                if (stopping) {
                    return;
                }
                LOG.warn("[tunnel] Stream ended by server.");
                scheduleReconnect();
            }
        });

        requestStream = stream;

        // Registration frame: identifies this worker to the proxy.
        writeToStream(
                stream,
                Frame.newBuilder().setWorkerId(config.getTunnelWorkerId()).build());

        LOG.info("[tunnel] Connected and waiting for requests.");
    }

    /** Hands the frame to its own virtual thread, bound to the stream it arrived on. */
    private void dispatch(Frame frame) {
        if (frame.getRequestId().isEmpty() || frame.getMethod().isEmpty()) {
            return; // registration ack / keepalive
        }
        StreamObserver<Frame> stream = this.requestStream;
        try {
            frameExecutor.execute(() -> handleFrame(frame, stream));
        } catch (RejectedExecutionException e) {
            LOG.warn("[tunnel] Dropping frame {}: client is stopping", frame.getRequestId());
        }
    }

    private void scheduleReconnect() {
        if (stopping) {
            return;
        }
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(
                (long) (config.getTunnelReconnectIntervalMs() * Math.pow(2, attempt - 1)), MAX_RECONNECT_INTERVAL_MS);
        LOG.info("[tunnel] Reconnecting in {}ms (attempt {})...", delay, attempt);

        ManagedChannel old = this.channel;
        if (old != null) {
            old.shutdownNow();
        }
        this.requestStream = null;
        try {
            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ignored) {
            // scheduler already shut down
        }
    }

    private void handleFrame(Frame frame, StreamObserver<Frame> stream) {
        String httpMethod =
                frame.getHttpMethod().isEmpty() ? "POST" : frame.getHttpMethod().toUpperCase(Locale.ROOT);
        String methodPath = frame.getMethod().replaceFirst("^/", "");
        Frame reply;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + localPort + "/" + methodPath))
                    .timeout(requestTimeout)
                    .method(
                            httpMethod,
                            HttpRequest.BodyPublishers.ofByteArray(
                                    frame.getPayload().toByteArray()))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

            reply = Frame.newBuilder()
                    .setWorkerId(config.getTunnelWorkerId())
                    .setRequestId(frame.getRequestId())
                    .setMethod(frame.getMethod())
                    .setPayload(ByteString.copyFrom(response.body()))
                    .setStatusCode(response.statusCode())
                    .setHttpMethod(httpMethod)
                    .build();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LOG.error("[tunnel] Failed to process frame {}: {}", frame.getRequestId(), message);
            reply = Frame.newBuilder()
                    .setWorkerId(config.getTunnelWorkerId())
                    .setRequestId(frame.getRequestId())
                    .setMethod(frame.getMethod())
                    .setPayload(ByteString.copyFromUtf8(errorEnvelope(message)))
                    .setStatusCode(500)
                    .setHttpMethod(httpMethod)
                    .build();
        }

        if (stream != this.requestStream) {
            LOG.warn("[tunnel] Dropping response for {}: the stream was reconnected", frame.getRequestId());
            return;
        }
        writeToStream(stream, reply);
    }

    /** SDK error envelope for a request that could not be forwarded to the local server. */
    static String errorEnvelope(String message) {
        return Json.toString(Map.of("body", "", "headers", Map.of("result-code", "1006", "result-message", message)));
    }

    /** Single write path for the stream; gRPC stream observers are not thread-safe. */
    private synchronized void writeToStream(StreamObserver<Frame> stream, Frame frame) {
        if (stream == null) {
            LOG.warn("[tunnel] Cannot send frame for {}: stream disconnected", frame.getRequestId());
            return;
        }
        try {
            stream.onNext(frame);
        } catch (RuntimeException e) {
            LOG.warn("[tunnel] Failed to send frame for {}: {}", frame.getRequestId(), e.getMessage());
        }
    }

    private static String normalizeTarget(String url) {
        String t = url;
        if (t.startsWith("http://")) {
            t = t.substring("http://".length());
        } else if (t.startsWith("https://")) {
            t = t.substring("https://".length());
        }
        return t;
    }
}
