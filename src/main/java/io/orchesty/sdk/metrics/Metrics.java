package io.orchesty.sdk.metrics;

import io.orchesty.sdk.application.WorkerApiClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends worker metrics to the Worker-API.
 * Process metrics go to the {@code monolith} measurement and curl metrics to the
 * {@code connectors} measurement, each as {@code {fields, tags}}. CPU times are read
 * from {@code /proc/self/stat} on Linux (zeros elsewhere).
 */
public class Metrics {

    public static final String PROCESS_MEASUREMENT = "monolith";
    public static final String CURL_MEASUREMENT = "connectors";

    private static final Logger LOG = LoggerFactory.getLogger(Metrics.class);

    private final WorkerApiClient client;

    public Metrics(WorkerApiClient client) {
        this.client = client;
    }

    public static StartMetrics getCurrentMetrics() {
        return new StartMetrics(System.currentTimeMillis(), getCpuTimes());
    }

    public static Times getTimes(StartMetrics start) {
        StartMetrics end = getCurrentMetrics();
        return new Times(
                end.timestamp() - start.timestamp(),
                end.cpu().userTime() - start.cpu().userTime(),
                end.cpu().kernelTime() - start.cpu().kernelTime());
    }

    public boolean sendProcessMetrics(Times times, String topologyId, String nodeId, String correlationId) {
        Map<String, Object> tags = new LinkedHashMap<>();
        putIfPresent(tags, "topology_id", topologyId);
        putIfPresent(tags, "node_id", nodeId);
        putIfPresent(tags, "correlation_id", correlationId);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("created", Instant.now().toString());
        fields.put("fpm_request_total_duration", times.requestDuration());
        fields.put("fpm_cpu_user_time", times.userTime());
        fields.put("fpm_cpu_kernel_time", times.kernelTime());

        return send(PROCESS_MEASUREMENT, fields, tags);
    }

    /**
     * Sends one curl (outbound HTTP call) metric. Worker-API requires the {@code user_id} and
     * {@code node_id} tags and accepts {@code response_error} only as a non-empty string, so
     * calls without a process context are skipped and an empty error is left out.
     *
     * @param times elapsed times of the call
     * @param responseCode HTTP status code of the response
     * @param user user ID of the process
     * @param nodeId ID of the node that made the call
     * @param nodeName name of the node that made the call
     * @param topologyId ID of the topology
     * @param appKey key of the application used for the call
     * @param correlationId correlation ID of the process
     * @param url called URL
     * @param responseError error message of the call, or {@code null}
     * @return {@code true} if the Worker-API accepted the metric
     */
    public boolean sendCurlMetrics(
            Times times,
            int responseCode,
            String user,
            String nodeId,
            String nodeName,
            String topologyId,
            String appKey,
            String correlationId,
            String url,
            String responseError) {
        if (isEmpty(user) || isEmpty(nodeId)) {
            LOG.debug("[metrics] skipping curl metrics for {}: missing user or node id", url);
            return false;
        }

        Map<String, Object> tags = new LinkedHashMap<>();
        putIfPresent(tags, "user_id", user);
        putIfPresent(tags, "node_id", nodeId);
        putIfPresent(tags, "node_name", nodeName);
        putIfPresent(tags, "topology_id", topologyId);
        putIfPresent(tags, "application_id", appKey);
        putIfPresent(tags, "correlation_id", correlationId);
        putIfPresent(tags, "url", url);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("created", Instant.now().toString());
        fields.put("sent_request_total_duration", times.requestDuration());
        fields.put("response_code", responseCode);
        if (responseError != null && !responseError.isBlank()) {
            fields.put("response_error", responseError);
        }

        return send(CURL_MEASUREMENT, fields, tags);
    }

    private boolean send(String measurement, Map<String, Object> fields, Map<String, Object> tags) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fields", fields);
        body.put("tags", tags);
        try {
            WorkerApiClient.Response res = client.send("/metrics/" + measurement, "POST", body);
            if (res.status() >= 300) {
                LOG.warn("[metrics] Worker-API rejected {} metrics ({}): {}", measurement, res.status(), res.body());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            LOG.debug("[metrics] failed to send {} metrics: {}", measurement, e.getMessage());
            return false;
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (!isEmpty(value)) {
            map.put(key, value);
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    static CpuTimes getCpuTimes() {
        try {
            Path stat = Path.of("/proc/self/stat");
            if (!Files.exists(stat)) {
                return new CpuTimes(0, 0, 0);
            }
            String[] fields = Files.readString(stat).trim().split("\\s+");
            long utime = parse(fields, 13);
            long stime = parse(fields, 14);
            long cutime = parse(fields, 15);
            long cstime = parse(fields, 16);
            long startTime = parse(fields, 21);
            return new CpuTimes(utime + cutime, stime + cstime, startTime);
        } catch (Exception e) {
            return new CpuTimes(0, 0, 0);
        }
    }

    private static long parse(String[] fields, int index) {
        if (index < 0 || index >= fields.length) {
            return 0;
        }
        try {
            return Long.parseLong(fields[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Snapshot of CPU jiffies read from {@code /proc/self/stat}. */
    public record CpuTimes(long userTime, long kernelTime, long startTime) {}

    /** Timestamp + CPU snapshot captured at the start of a measured section. */
    public record StartMetrics(long timestamp, CpuTimes cpu) {}

    /** Elapsed wall/CPU deltas between two snapshots. */
    public record Times(long requestDuration, long userTime, long kernelTime) {}
}
