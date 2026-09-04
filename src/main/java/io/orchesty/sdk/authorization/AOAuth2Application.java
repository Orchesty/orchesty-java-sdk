package io.orchesty.sdk.authorization;

import io.orchesty.sdk.application.AApplication;
import io.orchesty.sdk.application.ABasicApplication;
import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.enums.AuthorizationType;
import io.orchesty.sdk.application.enums.CoreForm;
import io.orchesty.sdk.application.form.Field;
import io.orchesty.sdk.application.form.FieldType;
import io.orchesty.sdk.application.form.Form;
import io.orchesty.sdk.application.form.FormStack;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.transport.HttpMethods;
import io.orchesty.sdk.transport.RequestDto;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for OAuth2 (authorization-code) applications.
 * Delegates the flow to {@link OAuth2Provider}, stores the token under
 * {@code authorization_form.token} and adds a read-only redirect URL field to the
 * authorization form.
 */
public abstract class AOAuth2Application extends AApplication implements IOAuth2Application {

    protected OAuth2Provider provider;

    protected AOAuth2Application(OAuth2Provider provider) {
        this.provider = provider;
    }

    /**
     * Sets the shared {@link OAuth2Provider} after construction.
     * Lets applications built before the {@link io.orchesty.sdk.WorkerServer} (e.g.
     * descriptor-driven Camel apps registered in {@code main}) receive the server's provider.
     * The provider is only used at request time, so it may be {@code null} until the server
     * sets it.
     *
     * @param provider OAuth2 provider
     * @return this application
     */
    public AOAuth2Application setProvider(OAuth2Provider provider) {
        this.provider = provider;
        return this;
    }

    protected OAuth2Provider requireProvider() {
        if (provider == null) {
            throw new IllegalStateException("OAuth2Provider has not been set for application [" + getName() + "].");
        }
        return provider;
    }

    public abstract String getAuthUrl();

    public abstract String getTokenUrl();

    public abstract List<String> getScopes(ApplicationInstall applicationInstall);

    @Override
    public String getAuthorizationType() {
        return AuthorizationType.OAUTH2;
    }

    /**
     * Builds the provider authorize URL. For custom apps it also stores a new one-time nonce on
     * the install (see {@link OAuth2StateNonce}); the caller must persist the install.
     *
     * @param applicationInstall application install to authorize
     * @return the authorize URL
     */
    @Override
    public String authorize(ApplicationInstall applicationInstall) {
        OAuth2Provider oauth = requireProvider();
        OAuth2Dto dto = createDto(applicationInstall, "");
        if (dto.isCustomApp()) {
            dto.setStateNonce(OAuth2StateNonce.create(applicationInstall));
        }
        return oauth.authorize(dto, getScopes(applicationInstall), getScopesSeparator());
    }

    @Override
    public boolean isAuthorized(ApplicationInstall applicationInstall) {
        IToken token = getTokens(applicationInstall);
        return token != null
                && token.getAccessToken() != null
                && !token.getAccessToken().isEmpty();
    }

    @Override
    public String getFrontendRedirectUrl(ApplicationInstall applicationInstall) {
        Map<String, Object> authForm = asMap(applicationInstall.getSettings().get(CoreForm.AUTHORIZATION_FORM));
        return authForm == null ? null : str(authForm.get(FRONTEND_REDIRECT_URL));
    }

    @Override
    public void setFrontendRedirectUrl(ApplicationInstall applicationInstall, String redirectUrl) {
        applicationInstall.addSettings(Map.of(CoreForm.AUTHORIZATION_FORM, Map.of(FRONTEND_REDIRECT_URL, redirectUrl)));
    }

    @Override
    public ApplicationInstall refreshAuthorization(ApplicationInstall applicationInstall) {
        IToken token =
                requireProvider().refreshAccessToken(createDto(applicationInstall, ""), getTokens(applicationInstall));
        applyToken(applicationInstall, token);
        return applicationInstall;
    }

    @Override
    public void setAuthorizationToken(ApplicationInstall applicationInstall, Map<String, String> token) {
        IToken fromProvider = requireProvider()
                .getAccessToken(
                        createDto(applicationInstall, ""),
                        token.get("code"),
                        getScopes(applicationInstall),
                        getScopesSeparator());
        applyToken(applicationInstall, fromProvider);
    }

    public String getAccessToken(ApplicationInstall applicationInstall) {
        IToken token = getTokens(applicationInstall);
        if (token != null
                && token.getAccessToken() != null
                && !token.getAccessToken().isEmpty()) {
            return token.getAccessToken();
        }
        throw new IllegalStateException("There is no access token");
    }

    public OAuth2Dto createDto(ApplicationInstall applicationInstall, String redirectUrl) {
        OAuth2Dto dto = new OAuth2Dto(applicationInstall, getAuthUrl(), getTokenUrl());
        dto.setCustomAppDependencies(
                applicationInstall.getUser(), applicationInstall.getName(), applicationInstall.getSdk());
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            dto.setRedirectUrl(redirectUrl);
        }
        return dto;
    }

    public IToken getTokens(ApplicationInstall applicationInstall) {
        Map<String, Object> authForm = asMap(applicationInstall.getSettings().get(CoreForm.AUTHORIZATION_FORM));
        if (authForm == null) {
            return null;
        }
        Map<String, Object> tokenMap = asMap(authForm.get(ABasicApplication.TOKEN));
        return tokenMap == null ? null : IToken.fromMap(tokenMap);
    }

    /**
     * Builds the request and attaches {@code Authorization: Bearer <accessToken>} from the
     * stored token.
     *
     * @param dto process message the request is made for
     * @param applicationInstall application install holding the token
     * @param method HTTP method
     * @param url request URL
     * @param data request body, may be {@code null}
     * @return the prepared request
     */
    @Override
    public RequestDto getRequestDto(
            ProcessDto dto, ApplicationInstall applicationInstall, HttpMethods method, String url, Object data) {
        RequestDto request = super.getRequestDto(dto, applicationInstall, method, url, data);
        request.addHeader("Authorization", "Bearer " + getAccessToken(applicationInstall));
        return request;
    }

    @Override
    protected void postProcessForms(FormStack forms, ApplicationInstall applicationInstall) {
        for (Form form : forms.getForms()) {
            if (CoreForm.AUTHORIZATION_FORM.equals(form.getKey())) {
                form.addField(new Field(
                                FieldType.TEXT,
                                OAUTH_REDIRECT_URL,
                                "Redirect URL",
                                requireProvider().getRedirectUri())
                        .setReadOnly(true));
                return;
            }
        }
    }

    protected String getScopesSeparator() {
        return OAuth2Provider.SCOPE_SEPARATOR_COMMA;
    }

    private void applyToken(ApplicationInstall applicationInstall, IToken token) {
        if (token.getExpires() != null) {
            try {
                applicationInstall.setExpires(Instant.parse(token.getExpires()));
            } catch (RuntimeException ignored) {
                // non-ISO expiry - leave install expiry unset
            }
        }
        Map<String, Object> tokenSettings = new LinkedHashMap<>();
        tokenSettings.put(ABasicApplication.TOKEN, token.toMap());
        applicationInstall.addSettings(Map.of(CoreForm.AUTHORIZATION_FORM, tokenSettings));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
