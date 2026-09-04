package io.orchesty.sdk.crypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;

/**
 * Crypt provider implementing the Orchesty {@code WindWalkerCrypt} credential-encryption format.
 * The output is byte-compatible with other Orchesty workers: {@code ApplicationInstall} settings
 * encrypted by any worker with the same {@code CRYPT_SECRET} can be decrypted here, and the
 * other way round.
 *
 * <p>Wire format: {@code <prefix><b64(hmac)>:<b64(salt)>:<b64(iv)>:<b64(ciphertext)>}
 * <ul>
 *   <li>cipher: XSalsa20-Poly1305 (libsodium secretbox easy), 24-byte nonce.</li>
 *   <li>encryption key: the shared secret repeated/truncated to 32 bytes.</li>
 *   <li>HMAC key: PBKDF2-HMAC-SHA256(secretKey32, salt, 12000, 32B) -&gt; hex(64) -&gt; 2nd half (32 ASCII chars).</li>
 *   <li>integrity: HMAC-SHA256 over salt || iv || ciphertext (encrypt-then-MAC).</li>
 *   <li>plaintext: PHP-serialized before encryption.</li>
 * </ul>
 */
public class WindWalkerCrypt implements ICrypt {

    public static final String DEFAULT_PREFIX = "002_";

    private static final int PBKDF2_ITERATIONS = 12000;
    private static final int PBKDF2_HASH_BYTES = 32;
    private static final int SALT_BYTES = 32;
    private static final int HMAC_KEY_CACHE_SIZE = 1000;

    private final String secretKey;
    private final String prefix;
    private final SecureRandom random = new SecureRandom();

    /**
     * Derived HMAC keys by Base64 salt for {@link #decrypt(String)}; the PBKDF2 derivation is
     * expensive (12 000 iterations) and the same stored settings are decrypted repeatedly.
     */
    private final Map<String, byte[]> hmacKeyCache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > HMAC_KEY_CACHE_SIZE;
        }
    });

    public WindWalkerCrypt(String secretKey) {
        this(secretKey, DEFAULT_PREFIX);
    }

    public WindWalkerCrypt(String secretKey, String prefix) {
        if (prefix.length() != 4) {
            throw new IllegalArgumentException("Crypt prefix of class [WindWalkerCrypt] has bad length ["
                    + prefix.length() + "], allowed length is 4.");
        }
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalArgumentException("Crypt secret must not be empty.");
        }
        this.secretKey = secretKey;
        this.prefix = prefix;
        if (deriveKey().length != SecretBox.KEY_BYTES) {
            throw new IllegalArgumentException("Crypt secret must contain only ASCII characters.");
        }
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public String encrypt(Object data) {
        byte[] key = deriveKey();
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(SecretBox.NONCE_BYTES);
        // A fresh salt is never seen again by this instance, so it is not cached.
        byte[] hmacKey = pbkdf2HmacKey(key, salt);

        byte[] plaintext = PhpSerializer.serialize(data).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = SecretBox.easy(plaintext, iv, key);
        byte[] hmac = hmacSha256(hmacKey, concat(salt, iv, encrypted));

        Base64.Encoder b64 = Base64.getEncoder();
        return prefix
                + b64.encodeToString(hmac) + ":"
                + b64.encodeToString(salt) + ":"
                + b64.encodeToString(iv) + ":"
                + b64.encodeToString(encrypted);
    }

    @Override
    public Object decrypt(String data) {
        if (!data.startsWith(prefix)) {
            throw new IllegalArgumentException("Unknown prefix in hash.");
        }
        String raw = data.substring(prefix.length());
        String[] parts = raw.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Malformed WindWalkerCrypt payload.");
        }
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] hmac = b64.decode(parts[0]);
        byte[] salt = b64.decode(parts[1]);
        byte[] iv = b64.decode(parts[2]);
        byte[] encrypted = b64.decode(parts[3]);

        byte[] key = deriveKey();
        byte[] hmacKey = deriveHmacKey(key, salt);
        byte[] calculated = hmacSha256(hmacKey, concat(salt, iv, encrypted));
        if (!MessageDigest.isEqual(calculated, hmac)) {
            throw new SecurityException("HMAC Error: Invalid HMAC.");
        }

        byte[] plaintext = SecretBox.openEasy(encrypted, iv, key);
        return PhpSerializer.unserialize(new String(plaintext, StandardCharsets.UTF_8));
    }

    int cachedHmacKeyCount() {
        return hmacKeyCache.size();
    }

    private byte[] deriveKey() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < SecretBox.KEY_BYTES) {
            sb.append(secretKey);
        }
        return sb.substring(0, SecretBox.KEY_BYTES).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] deriveHmacKey(byte[] key, byte[] salt) {
        String cacheKey = Base64.getEncoder().encodeToString(salt);
        byte[] cached = hmacKeyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        // Derived outside the map lock, so concurrent derivations for different salts don't wait.
        byte[] derived = pbkdf2HmacKey(key, salt);
        hmacKeyCache.put(cacheKey, derived);
        return derived;
    }

    private static byte[] pbkdf2HmacKey(byte[] key, byte[] salt) {
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(key, salt, PBKDF2_ITERATIONS);
        KeyParameter kp = (KeyParameter) gen.generateDerivedMacParameters(PBKDF2_HASH_BYTES * 8);
        String hex = Hex.toHexString(kp.getKey()); // 64 chars
        String secondHalf = hex.substring(PBKDF2_HASH_BYTES, PBKDF2_HASH_BYTES * 2);
        return secondHalf.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private byte[] randomBytes(int size) {
        byte[] b = new byte[size];
        random.nextBytes(b);
        return b;
    }

    private static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) {
            len += a.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, off, a.length);
            off += a.length;
        }
        return out;
    }
}
