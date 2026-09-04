package io.orchesty.sdk.authorization;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 token as persisted under {@code authorization_form.token}.
 * Consists of {@code accessToken}, {@code tokenType}, {@code expires}, {@code refreshToken}
 * and any provider-specific {@code others}.
 */
public class IToken {

    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";
    public static final String TOKEN_TYPE = "tokenType";
    public static final String EXPIRES = "expires";
    public static final String OTHERS = "others";

    private String accessToken;
    private String tokenType;
    private String refreshToken;
    private String expires;
    private Map<String, Object> others = new LinkedHashMap<>();

    public String getAccessToken() {
        return accessToken;
    }

    public IToken setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public String getTokenType() {
        return tokenType;
    }

    public IToken setTokenType(String tokenType) {
        this.tokenType = tokenType;
        return this;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public IToken setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    public String getExpires() {
        return expires;
    }

    public IToken setExpires(String expires) {
        this.expires = expires;
        return this;
    }

    public Map<String, Object> getOthers() {
        return others;
    }

    public IToken setOthers(Map<String, Object> others) {
        this.others = others == null ? new LinkedHashMap<>() : others;
        return this;
    }

    /** Returns the map representation stored under {@code authorization_form.token}. */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(ACCESS_TOKEN, accessToken);
        out.put(TOKEN_TYPE, tokenType);
        out.put(EXPIRES, expires);
        out.put(OTHERS, others);
        if (refreshToken != null) {
            out.put(REFRESH_TOKEN, refreshToken);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static IToken fromMap(Map<String, Object> map) {
        IToken token = new IToken();
        if (map == null) {
            return token;
        }
        token.accessToken = str(map.get(ACCESS_TOKEN));
        token.tokenType = str(map.get(TOKEN_TYPE));
        token.refreshToken = str(map.get(REFRESH_TOKEN));
        token.expires = str(map.get(EXPIRES));
        Object others = map.get(OTHERS);
        if (others instanceof Map<?, ?> m) {
            token.others = new LinkedHashMap<>((Map<String, Object>) m);
        }
        return token;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
