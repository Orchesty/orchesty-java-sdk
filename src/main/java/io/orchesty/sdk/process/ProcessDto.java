package io.orchesty.sdk.process;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Message context passed to a node's {@code process} method.
 * Carries the payload ({@link #getData()} / {@link #getJsonData()}) and the Bridge
 * headers, and provides the result-setting helpers the Bridge understands.
 */
public class ProcessDto {

    private static final Set<ResultCode> ALLOWED_STOP_CODES =
            Set.of(ResultCode.STOP_AND_FAILED, ResultCode.DO_NOT_CONTINUE);

    private String data = "";
    private String currentApp = "";
    private Map<String, String> headers = new LinkedHashMap<>();
    // Object-pool marker; do not touch outside the pool.
    private boolean free = true;

    // ---------------------------------------------------------------- payload

    public String getData() {
        return data;
    }

    public ProcessDto setData(String data) {
        this.data = data;
        return this;
    }

    public Map<String, Object> getJsonData() {
        String d = (data == null || data.isEmpty()) ? "{}" : data;
        return Json.fromString(d, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    public <T> T getJsonData(Class<T> type) {
        String d = (data == null || data.isEmpty()) ? "{}" : data;
        return Json.fromString(d, type);
    }

    public ProcessDto setJsonData(Object body) {
        this.data = Json.toString(body);
        return this;
    }

    /** What the Bridge stores as the envelope {@code body} (a raw JSON string). */
    public Object getBridgeData() {
        return data;
    }

    // ---------------------------------------------------------------- headers

    public Map<String, String> getHeaders() {
        return headers;
    }

    public ProcessDto setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public ProcessDto addHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public ProcessDto removeHeader(String key) {
        headers.remove(key);
        return this;
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public String getHeader(String key, String defaultValue) {
        String v = headers.get(key);
        return v != null ? v : defaultValue;
    }

    public boolean hasHeader(String key) {
        return headers.containsKey(key);
    }

    public String getUser() {
        return headers.get(Headers.USER);
    }

    public ProcessDto setUser(String user) {
        headers.put(Headers.USER, user);
        return this;
    }

    public String getSdk() {
        return headers.get(Headers.SDK);
    }

    public String getCurrentApp() {
        return currentApp;
    }

    public ProcessDto setCurrentApp(String currentApp) {
        this.currentApp = currentApp;
        return this;
    }

    // ------------------------------------------------------------ result codes

    public ProcessDto setSuccessProcess(String message) {
        setStatusHeader(ResultCode.SUCCESS, message);
        return this;
    }

    public ProcessDto setSuccessProcess() {
        return setSuccessProcess("Message has been processed successfully.");
    }

    public ProcessDto setStopProcess(ResultCode status, String reason) {
        if (!ALLOWED_STOP_CODES.contains(status)) {
            throw new IllegalArgumentException("Value does not match with the required one");
        }
        setStatusHeader(status, reason);
        return this;
    }

    public ProcessDto setLimitExceeded(String reason) {
        setStatusHeader(ResultCode.LIMIT_EXCEEDED, reason);
        return this;
    }

    public ProcessDto setRepeater(int interval, int maxHops, String reason) {
        if (interval < 1) {
            throw new IllegalArgumentException("Value interval is obligatory and can not be lower than 0");
        }
        if (maxHops < 1) {
            throw new IllegalArgumentException("Value maxHops is obligatory and can not be lower than 0");
        }
        setStatusHeader(ResultCode.REPEAT, reason);
        addHeader(Headers.REPEAT_INTERVAL, Integer.toString(interval));
        addHeader(Headers.REPEAT_MAX_HOPS, Integer.toString(maxHops));
        return this;
    }

    public ProcessDto removeRepeater() {
        removeHeader(Headers.REPEAT_INTERVAL);
        removeHeader(Headers.REPEAT_MAX_HOPS);
        removeHeader(Headers.REPEAT_HOPS);
        removeHeader(Headers.REPEAT_QUEUE);
        removeRelatedHeaders(Set.of(ResultCode.REPEAT));
        return this;
    }

    public ProcessDto setLimiter(String key, int time, int amount) {
        addHeader(Headers.LIMITER_KEY, getLimiterKey(key, time, amount));
        return this;
    }

    /**
     * Sets a limiter with an additional group bucket.
     *
     * @param key limiter key
     * @param time time window in seconds
     * @param amount allowed number of requests in the window
     * @param groupKey group bucket key
     * @param groupTime group time window in seconds
     * @param groupAmount allowed number of requests for the group
     * @return this DTO
     */
    public ProcessDto setLimiterWithGroup(
            String key, int time, int amount, String groupKey, int groupTime, int groupAmount) {
        addHeader(Headers.LIMITER_KEY, getLimiterKeyWithGroup(key, time, amount, groupKey, groupTime, groupAmount));
        return this;
    }

    public ProcessDto removeLimiter() {
        removeHeader(Headers.LIMITER_KEY);
        return this;
    }

    /**
     * Forces the message to a specific subset of followers (by name).
     * Resolves follower names to queue ids from the {@code worker-followers} header and
     * sets the forward-to-target-queue result.
     *
     * @param followers names of the followers to route the message to
     * @return this DTO
     */
    public ProcessDto setForceFollowers(String... followers) {
        List<Map<String, Object>> workerFollowers = Json.fromString(
                getHeader(Headers.WORKER_FOLLOWERS, "[]"), new TypeReference<ArrayList<Map<String, Object>>>() {});
        List<String> wanted = Arrays.asList(followers);

        StringJoiner targetQueues = new StringJoiner(",");
        for (Map<String, Object> follower : workerFollowers) {
            Object name = follower.get("name");
            if (name != null && wanted.contains(String.valueOf(name))) {
                targetQueues.add(String.valueOf(follower.get("id")));
            }
        }

        if (targetQueues.length() == 0) {
            StringJoiner available = new StringJoiner(",");
            for (Map<String, Object> follower : workerFollowers) {
                available.add(String.valueOf(follower.get("name")));
            }
            throw new IllegalArgumentException("Inserted follower(s) [" + String.join(",", followers)
                    + "] can't be reached. Available follower(s) [" + available + "]");
        }

        addHeader(Headers.FORCE_TARGET_QUEUE, targetQueues.toString());
        setStatusHeader(
                ResultCode.FORWARD_TO_TARGET_QUEUE,
                "Message will be force re-routed to [" + targetQueues + "] follower(s).");
        return this;
    }

    public ProcessDto removeForceFollowers() {
        removeHeader(Headers.FORCE_TARGET_QUEUE);
        removeRelatedHeaders(Set.of(ResultCode.FORWARD_TO_TARGET_QUEUE));
        return this;
    }

    /**
     * Accumulates audit entity data into the {@code audit-entity} header.
     * Entries with the same {@code entity} share a single {@code key} and have their
     * {@code fields} appended.
     *
     * @param entity audit entity name
     * @param key entity key, used only when the entity is added for the first time
     * @param fields fields to append to the entity
     * @return this DTO
     */
    public ProcessDto addAuditHeader(String entity, String key, List<Map<String, String>> fields) {
        Map<String, Object> auditData = Json.fromString(
                getHeader(Headers.AUDIT_ENTITY, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});

        @SuppressWarnings("unchecked")
        Map<String, Object> existing = (Map<String, Object>) auditData.get(entity);
        if (existing != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> existingFields = (List<Map<String, String>>) existing.get("fields");
            existingFields.addAll(fields);
        } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", key);
            entry.put("fields", new ArrayList<>(fields));
            auditData.put(entity, entry);
        }

        addHeader(Headers.AUDIT_ENTITY, Json.toString(auditData));
        return this;
    }

    // ------------------------------------------------------------ object pool

    public boolean isFree() {
        return free;
    }

    /**
     * Marks this instance as free or in use in the object pool.
     * When {@code true} the payload, headers and current app are cleared so the instance
     * can be reused.
     *
     * @param free whether the instance is returned to the pool
     * @return this DTO
     */
    public ProcessDto setFree(boolean free) {
        if (free) {
            this.data = "";
            this.headers = new LinkedHashMap<>();
            this.currentApp = "";
        }
        this.free = free;
        return this;
    }

    public ProcessDto removeHeaders() {
        this.headers = new LinkedHashMap<>();
        return this;
    }

    private static String decorateLimitKey(String key) {
        return key.contains("|") ? key : key + "|";
    }

    private static String getLimiterKey(String key, int time, int amount) {
        return decorateLimitKey(key) + ";" + time + ";" + amount;
    }

    private static String getLimiterKeyWithGroup(
            String key, int time, int amount, String groupKey, int groupTime, int groupAmount) {
        return decorateLimitKey(key) + ";" + time + ";" + amount + ";" + decorateLimitKey(groupKey) + ";" + groupTime
                + ";" + groupAmount;
    }

    public int getRepeatHops() {
        return Integer.parseInt(getHeader(Headers.REPEAT_HOPS, "0"));
    }

    public int getRepeaterMaxHops() {
        return Integer.parseInt(getHeader(Headers.REPEAT_MAX_HOPS, "0"));
    }

    protected void setStatusHeader(ResultCode code, String message) {
        if (message != null) {
            addHeader(Headers.RESULT_MESSAGE, message.replaceAll("\\r?\\n|\\r", ""));
        }
        addHeader(Headers.RESULT_CODE, Integer.toString(code.code()));
    }

    protected void removeRelatedHeaders(Set<ResultCode> codes) {
        int current = Integer.parseInt(getHeader(Headers.RESULT_CODE, "0"));
        for (ResultCode c : codes) {
            if (c.code() == current) {
                removeHeader(Headers.RESULT_MESSAGE);
                removeHeader(Headers.RESULT_CODE);
                return;
            }
        }
    }
}
