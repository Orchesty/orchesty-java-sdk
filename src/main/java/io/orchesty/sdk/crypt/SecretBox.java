package io.orchesty.sdk.crypt;

import java.util.Arrays;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * Pure-Java implementation of libsodium's {@code crypto_secretbox_easy} /
 * {@code crypto_secretbox_open_easy} (NaCl secretbox = XSalsa20 + Poly1305),
 * using Bouncy Castle so no native library is required.
 *
 * The input/output layout matches libsodium "easy": {@code MAC(16) || ciphertext}.
 */
public final class SecretBox {

    public static final int KEY_BYTES = 32;
    public static final int NONCE_BYTES = 24;
    public static final int MAC_BYTES = 16;

    private SecretBox() {}

    /**
     * Encrypts and authenticates a message.
     *
     * @param message plaintext to encrypt
     * @param nonce 24-byte nonce
     * @param key 32-byte key
     * @return {@code MAC(16) || ciphertext}
     */
    public static byte[] easy(byte[] message, byte[] nonce, byte[] key) {
        validate(nonce, key);
        XSalsa20Engine cipher = new XSalsa20Engine();
        cipher.init(true, new ParametersWithIV(new KeyParameter(key), nonce));

        // First 32 bytes of the keystream form the Poly1305 one-time key and are
        // NOT used to encrypt the message (NaCl spec).
        byte[] subKey = new byte[32];
        cipher.processBytes(new byte[32], 0, 32, subKey, 0);

        byte[] ciphertext = new byte[message.length];
        cipher.processBytes(message, 0, message.length, ciphertext, 0);

        byte[] out = new byte[MAC_BYTES + ciphertext.length];
        Poly1305 poly = new Poly1305();
        poly.init(new KeyParameter(subKey));
        poly.update(ciphertext, 0, ciphertext.length);
        poly.doFinal(out, 0);
        System.arraycopy(ciphertext, 0, out, MAC_BYTES, ciphertext.length);
        return out;
    }

    /**
     * Verifies and decrypts {@code MAC(16) || ciphertext}.
     *
     * @param cipherWithMac MAC followed by the ciphertext
     * @param nonce 24-byte nonce
     * @param key 32-byte key
     * @return the decrypted plaintext
     */
    public static byte[] openEasy(byte[] cipherWithMac, byte[] nonce, byte[] key) {
        validate(nonce, key);
        if (cipherWithMac.length < MAC_BYTES) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        byte[] mac = Arrays.copyOfRange(cipherWithMac, 0, MAC_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(cipherWithMac, MAC_BYTES, cipherWithMac.length);

        XSalsa20Engine cipher = new XSalsa20Engine();
        cipher.init(false, new ParametersWithIV(new KeyParameter(key), nonce));
        byte[] subKey = new byte[32];
        cipher.processBytes(new byte[32], 0, 32, subKey, 0);

        Poly1305 poly = new Poly1305();
        poly.init(new KeyParameter(subKey));
        poly.update(ciphertext, 0, ciphertext.length);
        byte[] tag = new byte[MAC_BYTES];
        poly.doFinal(tag, 0);
        if (!constantTimeEquals(tag, mac)) {
            throw new SecurityException("Poly1305 verification failed");
        }

        byte[] plaintext = new byte[ciphertext.length];
        cipher.processBytes(ciphertext, 0, ciphertext.length, plaintext, 0);
        return plaintext;
    }

    private static void validate(byte[] nonce, byte[] key) {
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("Nonce must be " + NONCE_BYTES + " bytes");
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("Key must be " + KEY_BYTES + " bytes");
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
