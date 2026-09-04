package io.orchesty.sdk.node;

import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.storage.DatabaseClient;
import io.orchesty.sdk.transport.HttpSender;
import java.util.List;

/**
 * Base class for nodes that may depend on an {@link IApplication} for
 * authentication/configuration.
 * The {@link HttpSender} and {@link DatabaseClient} are injected by the runtime
 * (see {@code WorkerServer} / {@code DIContainer}) so a node can issue native
 * HTTP calls and persist documents without wiring them by hand.
 */
public abstract class ANode implements INode {

    private IApplication application;
    private HttpSender sender;
    private DatabaseClient db;

    public ANode setApplication(IApplication application) {
        this.application = application;
        return this;
    }

    public String getApplicationName() {
        return application != null ? application.getName() : "";
    }

    protected IApplication getApplication() {
        if (application != null) {
            return application;
        }
        throw new IllegalStateException("Application has not been set.");
    }

    protected boolean hasApplication() {
        return application != null;
    }

    public ANode setSender(HttpSender sender) {
        this.sender = sender;
        return this;
    }

    protected HttpSender getSender() {
        if (sender != null) {
            return sender;
        }
        throw new IllegalStateException("HttpSender has not been set.");
    }

    public ANode setDb(DatabaseClient db) {
        this.db = db;
        return this;
    }

    protected DatabaseClient getDb() {
        if (db != null) {
            return db;
        }
        throw new IllegalStateException("DatabaseClient has not been set.");
    }

    /**
     * Loads (and decrypts) the {@link ApplicationInstall} for the message's user/sdk and
     * this node's bound application.
     * A node that reads application settings must be registered <b>with</b> an application.
     *
     * @param dto message whose user and sdk headers identify the install
     * @return the matching application install
     */
    protected ApplicationInstall getApplicationInstallFromProcess(ProcessDto dto) {
        String appName = getApplicationName();
        if (appName == null || appName.isEmpty()) {
            throw new IllegalStateException("Application has not been set.");
        }
        String user = dto.getUser();
        if (user == null || user.isEmpty()) {
            throw new IllegalStateException("User not defined");
        }
        String sdk = dto.getSdk();
        ApplicationInstall install = getDb().getApplicationRepository()
                .findByNameAndUser(appName, user, List.of(sdk == null ? "" : sdk), true);
        if (install == null) {
            throw new IllegalStateException(
                    "ApplicationInstall for application [" + appName + "] and user [" + user + "] not found.");
        }
        return install;
    }
}
