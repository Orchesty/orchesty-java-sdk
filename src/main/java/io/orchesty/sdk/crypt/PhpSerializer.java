package io.orchesty.sdk.crypt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal but faithful PHP {@code serialize()} / {@code unserialize()} implementation.
 * {@link WindWalkerCrypt} PHP-serializes the plaintext of every {@code ApplicationInstall}
 * settings blob before encryption, so this format must match PHP exactly to stay compatible
 * with settings written by the Orchesty backend and other workers.
 *
 * <p>Supported types: null, boolean, integer, double, string, array (list/map).
 * Objects ({@code O:}) are intentionally unsupported - settings never contain them.
 * NaN and infinite doubles are rejected on serialize (settings come from JSON, which has
 * neither) but PHP's {@code INF}, {@code -INF} and {@code NAN} are read. An empty array
 * reads as an empty map.
 */
public final class PhpSerializer {

    private PhpSerializer() {}

    // ---------------------------------------------------------------- serialize

    public static String serialize(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("N;");
            return;
        }
        if (value instanceof String s) {
            writeString(sb, s);
            return;
        }
        if (value instanceof Boolean b) {
            sb.append("b:").append(b ? '1' : '0').append(';');
            return;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            sb.append("i:").append(((Number) value).longValue()).append(';');
            return;
        }
        if (value instanceof Double || value instanceof Float) {
            sb.append("d:").append(formatDouble(((Number) value).doubleValue())).append(';');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            sb.append("a:").append(map.size()).append(":{");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                writeKey(sb, e.getKey());
                write(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            sb.append("a:").append(list.size()).append(":{");
            for (int i = 0; i < list.size(); i++) {
                sb.append("i:").append(i).append(';');
                write(sb, list.get(i));
            }
            sb.append('}');
            return;
        }
        throw new IllegalArgumentException("Unsupported type for PHP serialize: " + value.getClass());
    }

    private static void writeKey(StringBuilder sb, Object key) {
        if (key instanceof Integer || key instanceof Long || key instanceof Short || key instanceof Byte) {
            sb.append("i:").append(((Number) key).longValue()).append(';');
        } else {
            writeString(sb, String.valueOf(key));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        int byteLen = s.getBytes(StandardCharsets.UTF_8).length;
        sb.append("s:").append(byteLen).append(":\"").append(s).append("\";");
    }

    private static String formatDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("Unsupported double for PHP serialize: " + d);
        }
        if (d == Math.rint(d)) {
            // Exact for any magnitude; a long cast would saturate at 2^63.
            return new BigDecimal(d).toPlainString();
        }
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }

    // -------------------------------------------------------------- unserialize

    public static Object unserialize(String data) {
        Parser p = new Parser(data.getBytes(StandardCharsets.UTF_8));
        return p.parseValue();
    }

    private static final class Parser {
        private final byte[] b;
        private int pos;

        Parser(byte[] bytes) {
            this.b = bytes;
        }

        Object parseValue() {
            char type = (char) b[pos];
            if (type == 'N') {
                pos += 2; // 'N' ';'
                return null;
            }
            pos++; // type char
            expect(':');
            switch (type) {
                case 'b': {
                    char c = (char) b[pos++];
                    expect(';');
                    return c == '1';
                }
                case 'i': {
                    long v = readLong();
                    expect(';');
                    return v;
                }
                case 'd': {
                    double v = readDouble();
                    expect(';');
                    return v;
                }
                case 's':
                    return parseString();
                case 'a':
                    return parseArray();
                default:
                    throw new IllegalArgumentException("Unsupported PHP token '" + type + "' at offset " + (pos - 2));
            }
        }

        private String parseString() {
            long len = readLong();
            expect(':');
            expect('"');
            byte[] raw = new byte[(int) len];
            System.arraycopy(b, pos, raw, 0, (int) len);
            pos += (int) len;
            expect('"');
            expect(';');
            return new String(raw, StandardCharsets.UTF_8);
        }

        private Object parseArray() {
            long n = readLong();
            expect(':');
            expect('{');
            LinkedHashMap<Object, Object> entries = new LinkedHashMap<>();
            boolean sequential = true;
            for (long i = 0; i < n; i++) {
                Object key = parseValue();
                Object val = parseValue();
                entries.put(key, val);
                if (!(key instanceof Long) || (Long) key != i) {
                    sequential = false;
                }
            }
            expect('}');
            if (n > 0 && sequential) {
                return new ArrayList<>(entries.values());
            }
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }

        private void expect(char c) {
            char actual = (char) b[pos];
            if (actual != c) {
                throw new IllegalArgumentException("Expected '" + c + "' but found '" + actual + "' at offset " + pos);
            }
            pos++;
        }

        private long readLong() {
            int start = pos;
            if ((char) b[pos] == '-') {
                pos++;
            }
            while (pos < b.length && Character.isDigit((char) b[pos])) {
                pos++;
            }
            return Long.parseLong(new String(b, start, pos - start, StandardCharsets.US_ASCII));
        }

        private double readDouble() {
            int start = pos;
            while (pos < b.length && (char) b[pos] != ';') {
                pos++;
            }
            String token = new String(b, start, pos - start, StandardCharsets.US_ASCII);
            return switch (token) {
                case "INF" -> Double.POSITIVE_INFINITY;
                case "-INF" -> Double.NEGATIVE_INFINITY;
                case "NAN" -> Double.NaN;
                default -> Double.parseDouble(token);
            };
        }
    }
}
