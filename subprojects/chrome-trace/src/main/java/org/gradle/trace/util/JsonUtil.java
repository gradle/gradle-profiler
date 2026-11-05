package org.gradle.trace.util;

public class JsonUtil {

    /**
     * Escapes a value for use inside a JSON string literal.
     */
    public static String escape(String value) {
        StringBuilder s = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                s.append('\\').append(c);
            } else if (c < 0x20) {
                s.append(String.format("\\u%04x", (int) c));
            } else {
                s.append(c);
            }
        }
        return s.toString();
    }
}
