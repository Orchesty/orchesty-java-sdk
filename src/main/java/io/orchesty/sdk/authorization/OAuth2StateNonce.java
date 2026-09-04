package io.orchesty.sdk.authorization;

import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.enums.CoreForm;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-time nonce that binds an OAuth2 callback to the authorize request that started it.
 * The nonce is stored in the install's (encrypted) {@code authorization_form} settings, sent
 * as the 4th part of the {@code state} and consumed by the global callback, so a forged or
 * replayed callback cannot store a token on someone else's install.
 */
public final class OAuth2StateNonce {

    /** Settings key under {@code authorization_form}. */
    public static final String KEY = "oauth_state_nonce";

    private static final SecureRandom RANDOM = new SecureRandom();

    private OAuth2StateNonce() {}

    /**
     * Generates a new 128-bit nonce and stores it on the install, replacing any previous one.
     *
     * @param install application install being authorized
     * @return the base64url-encoded nonce
     */
    public static String create(ApplicationInstall install) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        install.addSettings(Map.of(CoreForm.AUTHORIZATION_FORM, Map.of(KEY, nonce)));
        return nonce;
    }

    /**
     * Checks the nonce from the callback {@code state} against the stored one and removes the
     * stored nonce, so each authorize request can complete only once. A missing or different
     * nonce fails with an {@link IllegalStateException}.
     *
     * @param install application install the callback targets
     * @param nonce nonce decoded from the callback {@code state}
     */
    public static void consume(ApplicationInstall install, String nonce) {
        Map<String, Object> settings = new LinkedHashMap<>(install.getSettings());
        Map<String, Object> form = settings.get(CoreForm.AUTHORIZATION_FORM) instanceof Map<?, ?> m
                ? new LinkedHashMap<>(castMap(m))
                : new LinkedHashMap<>();
        Object stored = form.remove(KEY);
        if (stored == null
                || nonce == null
                || nonce.isEmpty()
                || !MessageDigest.isEqual(
                        String.valueOf(stored).getBytes(StandardCharsets.UTF_8),
                        nonce.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Invalid OAuth2 state.");
        }
        settings.put(CoreForm.AUTHORIZATION_FORM, form);
        install.setSettings(settings);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
