package io.orchesty.sdk.node;

import io.orchesty.sdk.application.IApplication;

/**
 * Base class for custom (mapper/filter) nodes.
 * A pure data-transformation node that may optionally be bound to an application so it can
 * read form settings via {@link #getApplicationInstallFromProcess(io.orchesty.sdk.process.ProcessDto)}.
 *
 * <p>It is both an {@link ANode} (application holder + runtime-injected
 * {@link io.orchesty.sdk.transport.HttpSender}/{@link io.orchesty.sdk.storage.DatabaseClient})
 * and an {@link ICustomNode}, so it registers on the {@code custom-node} routes. When a custom
 * node needs settings, register it <b>with</b> the application:
 * {@code registry.registerCustomNode(new MyMapper().setApplication(app))}.
 */
public abstract class ACommonNode extends ANode implements ICustomNode {

    @Override
    public ACommonNode setApplication(IApplication application) {
        super.setApplication(application);
        return this;
    }
}
