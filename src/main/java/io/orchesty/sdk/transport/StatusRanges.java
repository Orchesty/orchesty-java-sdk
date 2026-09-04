package io.orchesty.sdk.transport;

import java.util.List;

/**
 * Evaluates HTTP status codes against range specs.
 * Shared by the native {@link HttpSender} and the Camel adapter; supports exact codes, half-open
 * {@code from-to} ranges ({@code st >= from && st < to}) and the {@code <, <=, >, >=} prefixes.
 */
public final class StatusRanges {

    private StatusRanges() {}

    public static boolean inRange(int status, List<Object> ranges) {
        if (ranges == null) {
            return false;
        }
        for (Object it : ranges) {
            if (it instanceof Number n) {
                if (status == n.intValue()) {
                    return true;
                }
            } else if (it instanceof String s) {
                if (matchToken(status, s.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchToken(int st, String token) {
        try {
            if (token.startsWith("<=")) {
                return st <= Integer.parseInt(token.substring(2));
            }
            if (token.startsWith(">=")) {
                return st >= Integer.parseInt(token.substring(2));
            }
            if (token.startsWith("<")) {
                return st < Integer.parseInt(token.substring(1));
            }
            if (token.startsWith(">")) {
                return st > Integer.parseInt(token.substring(1));
            }
            if (token.contains("-")) {
                String[] parts = token.split("-");
                int from = Integer.parseInt(parts[0].trim());
                int to = Integer.parseInt(parts[1].trim());
                return st >= from && st < to;
            }
            return st == Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
