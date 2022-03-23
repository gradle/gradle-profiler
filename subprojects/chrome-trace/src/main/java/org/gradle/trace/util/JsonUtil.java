package org.gradle.trace.util;

import org.gradle.internal.impldep.org.apache.commons.lang.StringEscapeUtils;

import java.io.StringWriter;

public class JsonUtil {
    /**
     * Escapes a string the JSON way.
     *
     * @param text The text to be escaped.
     * @return a string escaped according to the JSON format.
     */
    public static String escape(final String text) {
        return StringEscapeUtils.escapeJavaScript(text);
    }
}
