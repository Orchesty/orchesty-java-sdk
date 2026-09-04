package io.orchesty.sdk.node;

/**
 * Implemented by nodes that emit a business audit checkpoint.
 * The runtime inspects this on every node/connector/batch response and, when present,
 * adds the {@code audit-checkpoint} header built from {@link #getAuditCheckpoint()}.
 */
public interface IAuditCheckpointProvider {

    IAuditCheckpoint getAuditCheckpoint();
}
