package io.orchesty.sdk.application;

import io.orchesty.sdk.application.form.FormStack;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.transport.HttpMethods;
import io.orchesty.sdk.transport.RequestDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract of an application rendered in the Orchesty UI: identity, authorization type,
 * configuration forms and install lifecycle.
 */
public interface IApplication {

    String getName();

    String getPublicName();

    String getDescription();

    String getAuthorizationType();

    String getApplicationType();

    boolean isInstallable();

    String getLogo();

    String getInfo();

    FormStack getFormStack();

    boolean isAuthorized(ApplicationInstall applicationInstall);

    Map<String, Object> getApplicationForms(ApplicationInstall applicationInstall);

    ApplicationInstall saveApplicationForms(ApplicationInstall applicationInstall, Map<String, Object> settings);

    ApplicationInstall savePassword(
            ApplicationInstall applicationInstall, String formKey, String fieldKey, String password);

    Map<String, Object> toArray();

    /**
     * Builds an authenticated outbound HTTP request for the native transport.
     * Applications add their authentication (bearer token, basic credentials, API-key header)
     * here; the default builds an unauthenticated request, and {@code ABasicApplication} /
     * {@code AOAuth2Application} override it to inject credentials.
     *
     * @param dto the current process message (used as {@code debugInfo})
     * @param install the configured application install (holds credentials)
     * @param method HTTP method
     * @param url absolute request URL
     * @param data optional request body (serialized to JSON when not a String)
     * @return the prepared request
     */
    default RequestDto getRequestDto(
            ProcessDto dto, ApplicationInstall install, HttpMethods method, String url, Object data) {
        RequestDto request = new RequestDto(url, method, dto);
        if (data != null) {
            if (data instanceof String s) {
                request.setBody(s);
            } else {
                request.setJsonBody(data);
            }
        }
        return request;
    }

    /**
     * Lists the synchronous actions this application offers in the UI, served at
     * {@code GET /applications/{name}/sync/list}. The lifecycle callbacks are not listed.
     * Override together with {@link #runSynchronousAction(String, Map)} when adding actions.
     *
     * @return the action names (without the {@code sync} prefix)
     */
    default List<String> getSyncMethods() {
        return List.of();
    }

    /**
     * Executes a synchronous action invoked by the platform at
     * {@code POST/GET /applications/{name}/sync/{method}}.
     * The platform calls the lifecycle callbacks {@code afterInstallCallback},
     * {@code afterUninstallCallback}, {@code afterEnableCallback} and {@code afterDisableCallback}
     * during install / uninstall / enable (activate) / disable and treats a non-2xx response
     * as fatal, which surfaces in the UI as "Cannot activate application".
     *
     * <p>By default the four lifecycle callbacks are no-ops returning {@code {"status":"ok"}}
     * and any other method is rejected. Override to react to a lifecycle event (e.g. trigger
     * a topology) or to add custom sync actions.
     *
     * @param method the action name from the URL (without the {@code sync} prefix)
     * @param body the parsed JSON request body (typically {@code {user,name,sdk}})
     * @return the action result serialized as the JSON response
     */
    default Object runSynchronousAction(String method, Map<String, Object> body) {
        switch (method) {
            case "afterInstallCallback":
            case "afterUninstallCallback":
            case "afterEnableCallback":
            case "afterDisableCallback":
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("status", "ok");
                return ok;
            default:
                throw new IllegalArgumentException(
                        "Method [" + method + "] has not been found in application [" + getName() + "].");
        }
    }
}
