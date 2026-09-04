package io.orchesty.sdk.crypt;

/**
 * A crypt provider that encrypts data to a prefixed string and decrypts it back.
 * The {@code prefix} (4 chars, e.g. {@code 002_}) selects the provider on decrypt.
 */
public interface ICrypt {

    String NAME = "ICrypt";

    String getPrefix();

    default int getPrefixLength() {
        return getPrefix().length();
    }

    default String getType() {
        return NAME;
    }

    String encrypt(Object data);

    Object decrypt(String data);
}
