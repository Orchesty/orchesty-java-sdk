package io.orchesty.sdk.node;

import io.orchesty.sdk.process.ProcessDto;

/**
 * Convenience base for a pass-through audit checkpoint node.
 * It does not transform the payload; it only declares the {@link IAuditCheckpoint}
 * that the runtime turns into the {@code audit-checkpoint} response header.
 * Subclasses provide {@link #getName()} and {@link #getAuditCheckpoint()}.
 */
public abstract class AuditCheckpointNode implements ICustomNode, IAuditCheckpointProvider {

    @Override
    public ProcessDto process(ProcessDto dto) {
        return dto;
    }
}
