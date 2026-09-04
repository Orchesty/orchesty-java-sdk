package io.orchesty.sdk.node;

import io.orchesty.sdk.process.ProcessDto;

/**
 * Executable Orchesty node.
 * The Bridge invokes {@link #process(ProcessDto)} once per message; the returned
 * {@link ProcessDto} carries the payload and result headers back to the Bridge.
 */
public interface INode {

    String getName();

    ProcessDto process(ProcessDto dto) throws Exception;
}
