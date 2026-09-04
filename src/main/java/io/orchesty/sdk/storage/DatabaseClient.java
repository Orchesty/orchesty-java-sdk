package io.orchesty.sdk.storage;

import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.ApplicationInstallRepository;
import io.orchesty.sdk.application.WorkerApiClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facade over the Worker-API document store with a registry of {@link Repository} instances.
 * Repositories are keyed by their collection name.
 */
public class DatabaseClient {

    private final WorkerApiClient client;
    private final Map<String, Repository<?>> repositories = new LinkedHashMap<>();

    public DatabaseClient(WorkerApiClient client) {
        this.client = client;
    }

    public WorkerApiClient getClient() {
        return client;
    }

    public DatabaseClient register(Repository<?> repository) {
        repositories.put(repository.getCollection(), repository);
        return this;
    }

    public Repository<?> getRepository(String collection) {
        Repository<?> repository = repositories.get(collection);
        if (repository == null) {
            throw new IllegalArgumentException("Repository for collection [" + collection + "] is not registered.");
        }
        return repository;
    }

    public ApplicationInstallRepository getApplicationRepository() {
        return (ApplicationInstallRepository) getRepository(ApplicationInstall.COLLECTION);
    }
}
