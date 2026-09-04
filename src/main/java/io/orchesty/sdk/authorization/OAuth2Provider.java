package io.orchesty.sdk.authorization;

import io.orchesty.sdk.transport.HttpMethods;
import io.orchesty.sdk.transport.HttpSender;
import io.orchesty.sdk.transport.RequestDto;
import io.orchesty.sdk.transport.ResponseDto;
import io.orchesty.sdk.transport.ResultRanges;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 authorization-code provider that builds authorize URLs and exchanges or refreshes
 * tokens via the {@link HttpSender}, without an external OAuth library.
 * Client credentials are sent in the form-encoded request body.
 */
public class OAuth2Provider {

    /** Comma scope separator, used by default. */
    public static final String SCOPE_SEPARATOR_COMMA = ",";

    public static final String SCOPE_SEPARATOR_SPACE = " ";

    private final String backend;
    private final HttpSender sender;

    public OAuth2Provider(String backend, HttpSender sender) {
        this.backend = backend == null ? "" : backend;
        this.sender = sender;
    }

    /** The global redirect URI the platform proxies OAuth callbacks through. */
    public String getRedirectUri() {
        return backend.replaceAll("/+$", "") + "/api/applications/authorize/token";
    }

    /**
     * Encodes {@code user:app:sdk}, plus {@code :nonce} when the DTO has one, as base64url.
     * The platform backend routes the callback by the 3rd part ({@code sdk}).
     *
     * @param dto OAuth2 settings of the application install
     * @return the encoded state
     */
    public static String stateEncode(OAuth2Dto dto) {
        String raw = dto.getUser() + ":" + dto.getApplicationKey() + ":" + dto.getSdk();
        if (!dto.getStateNonce().isEmpty()) {
            raw += ":" + dto.getStateNonce();
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static State stateDecode(String state) {
        String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        String[] parts = decoded.split(":", -1);
        return new State(part(parts, 0), part(parts, 1), part(parts, 2), part(parts, 3));
    }

    private static String part(String[] parts, int index) {
        return parts.length > index ? parts[index] : "";
    }

    /**
     * Builds the provider authorize URL (with {@code access_type=offline}).
     *
     * @param dto OAuth2 settings of the application install
     * @param scopes requested scopes
     * @param separator separator used to join the scopes
     * @return the authorize URL
     */
    public String authorize(OAuth2Dto dto, List<String> scopes, String separator) {
        String state = dto.isCustomApp() ? stateEncode(dto) : "";
        String redirectUri = dto.isRedirectUrl() ? dto.getRedirectUrl() : getRedirectUri();

        StringBuilder url = new StringBuilder(dto.getAuthorizationUrl());
        url.append(dto.getAuthorizationUrl().contains("?") ? "&" : "?");
        url.append("response_type=code");
        url.append("&client_id=").append(enc(dto.getClientId()));
        url.append("&redirect_uri=").append(enc(redirectUri));
        url.append("&scope=").append(enc(String.join(separator, scopes)));
        url.append("&state=").append(enc(state));
        url.append("&access_type=offline");
        return url.toString();
    }

    /**
     * Exchanges an authorization {@code code} for tokens.
     *
     * @param dto OAuth2 settings of the application install
     * @param code authorization code received in the callback
     * @param scopes requested scopes
     * @param separator separator used to join the scopes
     * @return the received token
     */
    public IToken getAccessToken(OAuth2Dto dto, String code, List<String> scopes, String separator) {
        String redirectUri = dto.isRedirectUrl() ? dto.getRedirectUrl() : getRedirectUri();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("scope", String.join(separator, scopes));
        params.put("client_id", dto.getClientId());
        params.put("client_secret", dto.getClientSecret());
        return exchange(dto.getTokenUrl(), params);
    }

    /**
     * Refreshes tokens using a stored refresh token.
     * The previous refresh token is kept when the provider does not return a new one.
     *
     * @param dto OAuth2 settings of the application install
     * @param token current token holding the refresh token
     * @return the refreshed token
     */
    public IToken refreshAccessToken(OAuth2Dto dto, IToken token) {
        if (token == null
                || token.getRefreshToken() == null
                || token.getRefreshToken().isEmpty()) {
            throw new IllegalStateException("Refresh token not found! Refresh is not possible.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", token.getRefreshToken());
        params.put("client_id", dto.getClientId());
        params.put("client_secret", dto.getClientSecret());
        IToken refreshed = exchange(dto.getTokenUrl(), params);
        // Some providers omit refresh_token on refresh - keep the previous one.
        if (refreshed.getRefreshToken() == null) {
            refreshed.setRefreshToken(token.getRefreshToken());
        }
        return refreshed;
    }

    private IToken exchange(String tokenUrl, Map<String, String> params) {
        String form = formEncode(params);
        RequestDto request = new RequestDto(tokenUrl, HttpMethods.POST, null, form)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json");
        // KEEP_ALL: never throw on status - we translate token errors into a clear message.
        ResponseDto response = sender.send(request, ResultRanges.KEEP_ALL);
        if (response.getResponseCode() >= 300) {
            throw new IllegalStateException(
                    "OAuth2 token endpoint returned HTTP " + response.getResponseCode() + ": " + response.getBody());
        }
        return convert(response.getJsonBody());
    }

    private static IToken convert(Map<String, Object> body) {
        Object accessToken = body.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException(
                    "Token is not received or is not valid. Reason: [" + body.get("error") + "]");
        }
        IToken token = new IToken();
        token.setAccessToken(String.valueOf(accessToken));
        if (body.get("token_type") != null) {
            token.setTokenType(String.valueOf(body.get("token_type")));
        }
        if (body.get("refresh_token") != null) {
            token.setRefreshToken(String.valueOf(body.get("refresh_token")));
        }
        Object expiresIn = body.get("expires_in");
        if (expiresIn instanceof Number n) {
            token.setExpires(Instant.now().plusSeconds(n.longValue()).toString());
        } else if (body.get("expires_at") != null) {
            token.setExpires(String.valueOf(body.get("expires_at")));
        }
        Map<String, Object> others = new LinkedHashMap<>(body);
        others.remove("access_token");
        others.remove("token_type");
        others.remove("refresh_token");
        others.remove("expires_in");
        others.remove("expires_at");
        token.setOthers(others);
        return token;
    }

    private static String formEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * Decoded OAuth2 state: {@code user}, {@code name} (app key), {@code sdk} and the one-time
     * {@code nonce} (empty when absent).
     */
    public record State(String user, String name, String sdk, String nonce) {}
}
