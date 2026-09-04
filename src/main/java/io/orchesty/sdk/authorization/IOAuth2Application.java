package io.orchesty.sdk.authorization;

import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.IApplication;
import java.util.Map;

/**
 * Contract of an application authorized via the OAuth2 authorization-code flow.
 */
public interface IOAuth2Application extends IApplication {

    String CLIENT_ID = "client_id";
    String CLIENT_SECRET = "client_secret";
    String OAUTH_REDIRECT_URL = "redirect_url";
    String FRONTEND_REDIRECT_URL = "frontend_redirect_url";

    /**
     * Builds the provider authorize URL for an install. It may change the install's settings
     * (e.g. store the state nonce), so the caller persists the install afterwards.
     *
     * @param applicationInstall application install to authorize
     * @return the authorize URL
     */
    String authorize(ApplicationInstall applicationInstall);

    /**
     * Refreshes the stored token and stores the new one on the install.
     *
     * @param applicationInstall application install holding the token
     * @return the updated install
     */
    ApplicationInstall refreshAuthorization(ApplicationInstall applicationInstall);

    String getFrontendRedirectUrl(ApplicationInstall applicationInstall);

    void setFrontendRedirectUrl(ApplicationInstall applicationInstall, String redirectUrl);

    /**
     * Exchanges the callback parameters (with {@code code}) for a token and stores it on the install.
     *
     * @param applicationInstall application install to store the token on
     * @param token OAuth2 callback query parameters
     */
    void setAuthorizationToken(ApplicationInstall applicationInstall, Map<String, String> token);
}
