package org.gradle.trace.util;

public class FilePathUtil {

    /**
     * Replaces '\' in paths on windows with '/', because '\' is an escape character in JSON.
     * Writing it into the JSON trace files leads to unreadable files.
     */
    public static String normalizePathInDisplayName(String displayName) {
        return displayName.replace('\\', '/');
    }
}
