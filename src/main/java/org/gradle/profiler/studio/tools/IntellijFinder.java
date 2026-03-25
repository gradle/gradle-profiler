package org.gradle.profiler.studio.tools;

import java.io.File;
import java.util.Optional;

import static org.gradle.profiler.OperatingSystem.MAC_OS_APPLICATIONS_PATH;

public class IntellijFinder {
    private static final String INTELLIJ_PROFILER_HOME = Optional.ofNullable(System.getenv("INTELLIJ_PROFILER_HOME")).orElse(System.getProperty("idea.home"));

    /**
     * Locates the user's Intellij IDEA installation. Returns null when not found.
     * <p>
     * You can override location with the environment variable INTELLIJ_PROFILER_HOME.
     */
    public static File findIdeHome() {
        if (INTELLIJ_PROFILER_HOME != null) {
            File ideHome = new File(INTELLIJ_PROFILER_HOME);
            if (ideHome.exists()) {
                return ideHome;
            }
        }
        File applicationsDir = new File(MAC_OS_APPLICATIONS_PATH);
        if (!applicationsDir.isDirectory()) {
            return null;
        }
        for (File file : applicationsDir.listFiles()) {
            if (file.getName().matches("IntelliJ IDEA.*\\.app")) {
                return file;
            }
        }
        return null;
    }
}
