package io.orchesty.sdk.crypt;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encrypts and decrypts data using the {@link ICrypt} provider selected by prefix.
 * Unsupported values are normalized before serialization.
 */
public class CryptManager {

    public static final int PREFIX_LENGTH = 4;

    private final Map<String, ICrypt> providers = new LinkedHashMap<>();

    public CryptManager(List<ICrypt> providers) {
        for (ICrypt provider : providers) {
            if (ICrypt.NAME.equals(provider.getType())) {
                this.providers.put(provider.getPrefix(), provider);
            }
        }
    }

    public String encrypt(Object data) {
        return encrypt(data, null);
    }

    public String encrypt(Object data, String prefix) {
        return getImplementation(prefix).encrypt(normalize(data));
    }

    public Object decrypt(String data) {
        String prefix = data.length() >= PREFIX_LENGTH ? data.substring(0, PREFIX_LENGTH) : data;
        return getImplementation(prefix).decrypt(data);
    }

    private ICrypt getImplementation(String prefix) {
        if ((prefix == null || prefix.isEmpty()) && !providers.isEmpty()) {
            return providers.values().iterator().next();
        }
        ICrypt impl = providers.get(prefix);
        if (impl != null) {
            return impl;
        }
        if ("00_".equals(prefix)) {
            throw new IllegalStateException("The prefix was removed for license reasons.");
        }
        throw new IllegalStateException("Unknown crypt service prefix.");
    }

    /**
     * Recursively converts unsupported values ({@link Date}) in maps and lists to strings.
     *
     * @param value value to normalize
     * @return the normalized value
     */
    @SuppressWarnings("unchecked")
    private static Object normalize(Object value) {
        if (value instanceof Date d) {
            return d.toString();
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
                out.put(e.getKey(), normalize(e.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            java.util.ArrayList<Object> out = new java.util.ArrayList<>();
            for (Object item : list) {
                out.add(normalize(item));
            }
            return out;
        }
        return value;
    }
}
