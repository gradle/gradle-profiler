package org.gradle.profiler;

import com.typesafe.config.Config;

import java.util.List;

public class DeprecationWarnings {
    static boolean hasKeyWithDeprecatedFallback(Config config, String newKey, String oldKey) {
        if (config.hasPath(newKey)) {
            return true;
        }
        if (config.hasPath(oldKey)) {
            reportDeprecatedKey(oldKey, newKey);
            return true;
        }
        return false;
    }

    static String resolveKeyWithDeprecatedFallback(Config config, String newKey, String oldKey) {
        if (config.hasPath(newKey)) {
            return newKey;
        }
        reportDeprecatedKey(oldKey, newKey);
        return oldKey;
    }

    static List<String> getConfigWithDeprecatedFallback(Config config, String newKey, String oldKey, List<String> defaultValue) {
        if (config.hasPath(newKey)) {
            return ConfigUtil.strings(config, newKey, defaultValue);
        }
        if (config.hasPath(oldKey)) {
            reportDeprecatedKey(oldKey, newKey);
            return ConfigUtil.strings(config, oldKey, defaultValue);
        }
        return defaultValue;
    }

    private static void reportDeprecatedKey(String oldKey, String newKey) {
        System.err.println("WARNING: Scenario key '" + oldKey + "' is deprecated. Use '" + newKey + "' instead.");
    }
}
