package org.gradle.profiler.ide.tools;

import java.io.File;
import java.util.Optional;

import static org.gradle.profiler.OperatingSystem.MAC_OS_APPLICATIONS_PATH;

/**
 * Locates IntelliJ-based IDE installations (Android Studio or IntelliJ IDEA).
 */
public class IdeFinder {

    /**
     * Locates the Android Studio installation. Returns null when not found.
     * <p>
     * Override the location with the {@code STUDIO_PROFILER_HOME} environment variable
     * or the {@code studio.home} system property.
     */
    public static File findAndroidStudioHome() {
        return findHome("STUDIO_PROFILER_HOME", "studio.home", "Android Studio.*\\.app");
    }

    /**
     * Locates the IntelliJ IDEA installation. Returns null when not found.
     * <p>
     * Override the location with the {@code INTELLIJ_HOME} environment variable
     * or the {@code intellij.home} system property.
     */
    public static File findIntelliJHome() {
        return findHome("INTELLIJ_HOME", "intellij.home", "IntelliJ IDEA.*\\.app");
    }

    private static File findHome(String envVar, String sysProp, String appNamePattern) {
        String override = Optional.ofNullable(System.getenv(envVar)).orElse(System.getProperty(sysProp));
        if (override != null) {
            File home = new File(override);
            if (home.exists()) {
                return home;
            }
        }
        File applicationsDir = new File(MAC_OS_APPLICATIONS_PATH);
        if (!applicationsDir.isDirectory()) {
            return null;
        }
        for (File file : applicationsDir.listFiles()) {
            if (file.getName().matches(appNamePattern)) {
                return file;
            }
        }
        return null;
    }
}
