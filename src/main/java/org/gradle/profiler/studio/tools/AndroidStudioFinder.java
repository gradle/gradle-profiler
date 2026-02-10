package org.gradle.profiler.studio.tools;

import java.io.File;
import java.util.Optional;

import static org.gradle.profiler.OperatingSystem.MAC_OS_APPLICATIONS_PATH;

public class AndroidStudioFinder {
    private static final String STUDIO_PROFILER_HOME = Optional.ofNullable(System.getenv("STUDIO_PROFILER_HOME")).orElse(System.getProperty("studio.home"));

    /**
     * Locates the user's Android Studio installation. Returns null when not found.
     * <p>
     * You can override location with the environment variable STUDIO_PROFILER_HOME.
     */
    public static File findStudioHome() {
        if (STUDIO_PROFILER_HOME != null) {
            File studioHome = new File(STUDIO_PROFILER_HOME);
            if (studioHome.exists()) {
                return studioHome;
            }
        }
        File applicationsDir = new File(MAC_OS_APPLICATIONS_PATH);
        if (!applicationsDir.isDirectory()) {
            return null;
        }
        for (File file : applicationsDir.listFiles()) {
            if (file.getName().matches("Android Studio.*\\.app")) {
                return file;
            }
        }
        return null;
    }
}
