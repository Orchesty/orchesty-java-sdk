package io.orchesty.sdk.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindWalkerCryptTest {

    /**
     * Decrypts a golden-vector ciphertext produced by the PHP SDK.
     * Proves the full pipeline: key stretching + PBKDF2 HMAC-key + XSalsa20-Poly1305 + php-unserialize.
     */
    @Test
    void decryptsPhpGoldenVector() {
        WindWalkerCrypt crypt = new WindWalkerCrypt("ADFAF1A6A1SEASCA6FA6C1A26SEV6S6S26S2V6SVV+94S8363SDDV6SDV645");

        String php =
                "002_Cww5KOU6QJOs1UaKXPmxJNEmk4HS6jBs85VSGjis30k=:UIJEGDZf7wFSudwi7lq36AeiiSB4g5vzmeRYXKsd1bM=:MTtg0gS+2PsDOsFc6VuJuU0rKTjXIk1S:sAhh2siZDXLysLmcjPix1Iv5EMstzIqpEu79K4euqj6qmzbckk2l5TAhpwD7HGtsMWtEezBlQdK+Ptz51cLvCMNTQAQxlBimDBd1+PPsPso9Ldh0zSmBbq1kJJeqyNWq0oDwEDawbmCMv0itRxq/vlwGiypcCGY6gsQ+EuDld6SBZ6RezKUI7lZMBxJhz4MkLAQGwKg0aFdoCsjyjNZZRil6aMinokzKW9KLOuo1EaqnwLTPMl1E0oQXgtnwQiX3B5neFxml7RKHAV9BfWOQBVSHoZuK7t0WoZKfrsuElo7c8r0p2IwKlZByR+bPktpgtirUdG90Uw/KbPut1vUFqdPKK72lsEli35nMjUj18cM=";

        Object decrypted = crypt.decrypt(php);
        assertInstanceOf(Map.class, decrypted);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) decrypted;
        assertEquals("val", map.get("key"));
        assertTrue(map.containsKey("str"));

        Object inner = crypt.decrypt((String) map.get("str"));
        assertEquals("asdf12342~!@#$%^&*()_+{}|:\"<>?[]\\;,./", inner);
    }

    @Test
    void encryptThenDecryptRoundTrips() {
        WindWalkerCrypt crypt = new WindWalkerCrypt("123scycycxvxvdvse5678IDLNJNCSKJfefefefeffJKkjbkjbkjnsl");

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("user", "user");
        settings.put("password", "password");

        String encrypted = crypt.encrypt(settings);
        assertTrue(encrypted.startsWith("002_"));

        @SuppressWarnings("unchecked")
        Map<String, Object> decrypted = (Map<String, Object>) crypt.decrypt(encrypted);
        assertEquals("user", decrypted.get("user"));
        assertEquals("password", decrypted.get("password"));
    }

    @Test
    void nonDeterministicButBothDecrypt() {
        WindWalkerCrypt crypt = new WindWalkerCrypt("some-secret-key-value-1234567890");
        String a = crypt.encrypt(Map.of("k", "v"));
        String b = crypt.encrypt(Map.of("k", "v"));
        assertTrue(!a.equals(b), "encryption must be randomized (fresh salt+iv)");
        assertEquals("v", ((Map<?, ?>) crypt.decrypt(a)).get("k"));
        assertEquals("v", ((Map<?, ?>) crypt.decrypt(b)).get("k"));
    }

    @Test
    void handlesNestedAndSpecialValues() {
        WindWalkerCrypt crypt = new WindWalkerCrypt("ADFAF1A6A1SEASCA6FA6C1A26SEV6S6S26S2V6SVV+94S8363SDDV6SDV645");

        Map<String, Object> authForm = new LinkedHashMap<>();
        authForm.put("client_id", "ac1a0325-4a9b-41c1-8f9e-4993e3d5784e");
        authForm.put("nullableValue", null);
        authForm.put("nested", List.of("1", "2"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authorization_form", authForm);

        @SuppressWarnings("unchecked")
        Map<String, Object> decrypted = (Map<String, Object>) crypt.decrypt(crypt.encrypt(data));
        @SuppressWarnings("unchecked")
        Map<String, Object> outForm = (Map<String, Object>) decrypted.get("authorization_form");
        assertEquals("ac1a0325-4a9b-41c1-8f9e-4993e3d5784e", outForm.get("client_id"));
        assertTrue(outForm.containsKey("nullableValue"));
        assertEquals(List.of("1", "2"), outForm.get("nested"));
    }

    @Test
    void rejectsMissingOrEmptySecret() {
        assertThrows(IllegalArgumentException.class, () -> new WindWalkerCrypt(null));
        assertThrows(IllegalArgumentException.class, () -> new WindWalkerCrypt(""));
    }

    @Test
    void rejectsNonAsciiSecret() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> new WindWalkerCrypt("tajné-heslo"));
        assertEquals("Crypt secret must contain only ASCII characters.", e.getMessage());
    }

    @Test
    void cachesDerivedHmacKeyPerDecryptedSaltOnly() {
        WindWalkerCrypt crypt = new WindWalkerCrypt("some-secret-key-value-1234567890");
        List<String> encrypted = List.of(
                crypt.encrypt(Map.of("i", "0")), crypt.encrypt(Map.of("i", "1")), crypt.encrypt(Map.of("i", "2")));
        assertEquals(0, crypt.cachedHmacKeyCount());

        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < encrypted.size(); i++) {
                assertEquals(String.valueOf(i), ((Map<?, ?>) crypt.decrypt(encrypted.get(i))).get("i"));
            }
        }
        assertEquals(3, crypt.cachedHmacKeyCount());
    }

    @Test
    void rejectsUnknownPrefix() {
        WindWalkerCrypt crypt = new WindWalkerCrypt("secret");
        assertThrows(IllegalArgumentException.class, () -> crypt.decrypt("_unknown"));
    }

    @Test
    void rejectsTamperedHmac() {
        WindWalkerCrypt crypt = new WindWalkerCrypt("123scycycxvxvdvse5678IDLNJNCSKJfefefefeffJKkjbkjbkjnsl");
        String encrypted = crypt.encrypt(Map.of("user", "data"));
        String body = encrypted.substring(crypt.getPrefixLength());
        String[] parts = body.split(":");
        // flip the first base64 char of the HMAC while keeping valid base64 length
        char first = parts[0].charAt(0);
        parts[0] = (first == 'A' ? 'B' : 'A') + parts[0].substring(1);
        String tampered = "002_" + String.join(":", parts);
        assertThrows(SecurityException.class, () -> crypt.decrypt(tampered));
    }
}
