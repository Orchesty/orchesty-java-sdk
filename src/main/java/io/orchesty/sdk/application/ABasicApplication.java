package io.orchesty.sdk.application;

import io.orchesty.sdk.application.enums.AuthorizationType;
import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.transport.HttpMethods;
import io.orchesty.sdk.transport.RequestDto;
import java.util.Map;

/**
 * Convenience base for BASIC-auth style applications (username/password, API key,
 * host, etc.). "Authorized" means every field the app declares as required in its
 * authorization form is present in the authorization form settings.
 *
 * <p>An authorization form without any required field therefore makes the application
 * authorized even when nothing has been filled in. Mark the credential fields as required;
 * the worker logs a warning at registration when none is.
 */
public abstract class ABasicApplication extends AApplication {

    /** Authorization form field key holding the user name. */
    public static final String USER = "user";

    public static final String PASSWORD = "password";
    public static final String TOKEN = "token";

    @Override
    public String getAuthorizationType() {
        return AuthorizationType.BASIC;
    }

    /**
     * Tells whether the authorization form declares at least one required field, i.e. whether
     * {@link #isAuthorized(ApplicationInstall)} can ever be {@code false}.
     *
     * @return {@code true} when a required authorization field exists
     */
    public boolean hasRequiredAuthorizationField() {
        return getFormStack().getForms().stream()
                .filter(f -> CoreForm.AUTHORIZATION_FORM.equals(f.getKey()))
                .flatMap(f -> f.getFields().stream())
                .anyMatch(io.orchesty.sdk.application.form.Field::isRequired);
    }

    /**
     * Builds the request and attaches HTTP basic auth from the authorization form's
     * {@code user}/{@code password} when both are present.
     * Override for API-key headers or other schemes.
     *
     * @param dto process message the request is made for
     * @param applicationInstall application install holding the credentials
     * @param method HTTP method
     * @param url request URL
     * @param data request body, may be {@code null}
     * @return the prepared request
     */
    @Override
    public RequestDto getRequestDto(
            ProcessDto dto, ApplicationInstall applicationInstall, HttpMethods method, String url, Object data) {
        RequestDto request = super.getRequestDto(dto, applicationInstall, method, url, data);
        Map<String, Object> authForm = asMap(applicationInstall.getSettings().get(CoreForm.AUTHORIZATION_FORM));
        if (authForm != null) {
            Object user = authForm.get(USER);
            Object password = authForm.get(PASSWORD);
            if (user != null
                    && password != null
                    && !String.valueOf(user).isEmpty()
                    && !String.valueOf(password).isEmpty()) {
                request.setAuth(String.valueOf(user), String.valueOf(password));
            }
        }
        return request;
    }

    @Override
    public boolean isAuthorized(ApplicationInstall applicationInstall) {
        Map<String, Object> authForm = asMap(applicationInstall.getSettings().get(CoreForm.AUTHORIZATION_FORM));
        if (authForm == null) {
            return getFormStack().getForms().stream()
                    .filter(f -> CoreForm.AUTHORIZATION_FORM.equals(f.getKey()))
                    .flatMap(f -> f.getFields().stream())
                    .noneMatch(io.orchesty.sdk.application.form.Field::isRequired);
        }
        return getFormStack().getForms().stream()
                .filter(f -> CoreForm.AUTHORIZATION_FORM.equals(f.getKey()))
                .flatMap(f -> f.getFields().stream())
                .filter(io.orchesty.sdk.application.form.Field::isRequired)
                .allMatch(field -> {
                    Object v = authForm.get(field.getKey());
                    return v != null && !String.valueOf(v).isEmpty();
                });
    }
}
