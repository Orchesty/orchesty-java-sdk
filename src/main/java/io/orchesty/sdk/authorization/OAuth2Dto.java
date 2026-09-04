package io.orchesty.sdk.authorization;

import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.enums.CoreForm;
import java.util.Map;

/**
 * OAuth2 parameters of a single application install.
 * Holds the client id/secret from the authorization form, the authorize/token URLs, an
 * optional redirect URL and the custom-app dependencies (user/app/sdk and the one-time nonce)
 * encoded into the state.
 */
public class OAuth2Dto {

    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";

    private final String clientId;
    private final String clientSecret;
    private final String authorizeUrl;
    private final String tokenUrl;

    private String redirectUrl = "";
    private String user = "";
    private String applicationKey = "";
    private String sdk = "";
    private String stateNonce = "";

    public OAuth2Dto(ApplicationInstall install, String authorizeUrl, String tokenUrl) {
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        Map<String, Object> authForm = asMap(install.getSettings().get(CoreForm.AUTHORIZATION_FORM));
        this.clientId = authForm == null ? "" : str(authForm.get(CLIENT_ID));
        this.clientSecret = authForm == null ? "" : str(authForm.get(CLIENT_SECRET));
    }

    public String getApplicationKey() {
        return applicationKey;
    }

    public String getAuthorizationUrl() {
        return authorizeUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public String getUser() {
        return user;
    }

    public String getSdk() {
        return sdk;
    }

    public String getStateNonce() {
        return stateNonce;
    }

    public OAuth2Dto setStateNonce(String stateNonce) {
        this.stateNonce = stateNonce == null ? "" : stateNonce;
        return this;
    }

    public boolean isCustomApp() {
        return user != null && !user.isEmpty() && applicationKey != null && !applicationKey.isEmpty();
    }

    public boolean isRedirectUrl() {
        return redirectUrl != null && !redirectUrl.isEmpty();
    }

    public void setCustomAppDependencies(String user, String applicationName, String sdk) {
        this.user = user;
        this.applicationKey = applicationName;
        this.sdk = sdk;
    }

    public OAuth2Dto setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
        return this;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
