package io.orchesty.sdk.node;

import io.orchesty.sdk.process.BatchProcessDto;

/**
 * Batch node (prefix {@code hbpf.batch}) that can emit many messages per invocation.
 * Unlike a connector it returns a {@link BatchProcessDto} holding <b>many</b> messages,
 * which the Bridge splits and fans out to follower(s).
 *
 * <p>Not a sub-type of {@link INode}: its {@code process} takes and returns a
 * {@link BatchProcessDto} (batch-specific envelope), so it deliberately does not
 * share {@link INode#process}.
 */
public interface IBatch {

    String getName();

    String getApplicationName();

    BatchProcessDto process(BatchProcessDto dto) throws Exception;
}
