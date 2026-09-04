package io.orchesty.sdk.node;

/**
 * Node that calls an external system, delegating auth to an application.
 * Registered under the {@code hbpf.connector} prefix.
 */
public interface IConnector extends INode {

    String getApplicationName();
}
