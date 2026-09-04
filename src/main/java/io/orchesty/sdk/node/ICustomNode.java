package io.orchesty.sdk.node;

/**
 * Pure data transformation node (mapper / filter).
 * Makes no HTTP calls, has no application dependency and is registered under the
 * {@code hbpf.custom-node} prefix.
 */
public interface ICustomNode extends INode {}
