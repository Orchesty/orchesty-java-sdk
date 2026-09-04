package io.orchesty.sdk.node;

import io.orchesty.sdk.application.IApplication;

/**
 * Base class for connectors: an {@link ANode} that also exposes its application
 * name for discovery/listing.
 */
public abstract class AConnector extends ANode implements IConnector {

    @Override
    public AConnector setApplication(IApplication application) {
        super.setApplication(application);
        return this;
    }
}
