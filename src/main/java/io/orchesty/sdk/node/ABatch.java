package io.orchesty.sdk.node;

import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.storage.DatabaseClient;
import io.orchesty.sdk.transport.HttpSender;
import java.util.List;

/**
 * Base class for batch nodes holding an optional application and the runtime-injected
 * {@link HttpSender}/{@link DatabaseClient}.
 * It cannot extend {@link ANode}, because {@link ANode} implements {@link INode} whose
 * {@code process(ProcessDto)} signature clashes with a batch's
 * {@code process(BatchProcessDto)}; the small holder logic is therefore duplicated here.
 */
public abstract class ABatch implements IBatch {

    private IApplication application;
    private HttpSender sender;
    private DatabaseClient db;

    public ABatch setApplication(IApplication application) {
        this.application = application;
        return this;
    }

    @Override
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

    public ABatch setSender(HttpSender sender) {
        this.sender = sender;
        return this;
    }

    protected HttpSender getSender() {
        if (sender != null) {
            return sender;
        }
        throw new IllegalStateException("HttpSender has not been set.");
    }

    public ABatch setDb(DatabaseClient db) {
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
     * this batch's bound application.
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
