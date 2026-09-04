package io.orchesty.sdk.process;

/**
 * Names of the standard Bridge &lt;-&gt; Worker headers.
 * Values are carried in the {@link MessageDto} envelope, not real HTTP headers.
 */
public final class Headers {

    // Framework headers
    public static final String PREV_CORRELATION_ID = "previous-correlation-id";
    public static final String CORRELATION_ID = "correlation-id";
    public static final String PROCESS_ID = "process-id";
    public static final String PARENT_ID = "parent-id";
    public static final String SEQUENCE_ID = "sequence-id";
    public static final String PREV_NODE_ID = "previous-node-id";
    public static final String NODE_ID = "node-id";
    public static final String NODE_NAME = "node-name";
    public static final String TOPOLOGY_ID = "topology-id";
    public static final String TOPOLOGY_NAME = "topology-name";
    public static final String USER = "user";
    public static final String WORKER_FOLLOWERS = "worker-followers";
    public static final String FORCE_TARGET_QUEUE = "force-target-queue";
    public static final String APPLICATIONS = "applications";
    public static final String ORCHESTY_API_KEY = "orchesty-api-key";

    // Result headers
    public static final String RESULT_CODE = "result-code";
    public static final String RESULT_MESSAGE = "result-message";
    public static final String RESULT_DETAIL = "result-detail";

    // Repeater headers
    public static final String REPEAT_QUEUE = "repeat-queue";
    public static final String REPEAT_INTERVAL = "repeat-interval";
    public static final String REPEAT_MAX_HOPS = "repeat-max-hops";
    public static final String REPEAT_HOPS = "repeat-hops";

    // Limiter headers
    public static final String LIMITER_KEY = "limiter-key";

    // Batch headers
    public static final String BATCH_CURSOR = "cursor";

    // Audit headers
    public static final String AUDIT_ENTITY = "audit-entity";
    public static final String AUDIT_ENTITY_IDS = "audit-entity-ids";
    public static final String AUDIT_CHECKPOINT = "audit-checkpoint";

    // SDK header
    public static final String SDK = "sdk";

    private Headers() {}
}
