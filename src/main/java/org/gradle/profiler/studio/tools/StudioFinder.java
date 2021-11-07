package org.gradle.profiler.studio.tools;

import java.io.File;

public class StudioFinder {

    static final String ENVIRONMENT_VARIABLE = "STUDIO_PROFILER_HOME";
    private static final String STUDIO_PROFILER_HOME = System.getenv(ENVIRONMENT_VARIABLE);

    /**
     * Locates the user's Android Studio installation. Returns null when not found.
     *
     * You can override location with the environment variable STUDIO_PROFILER_HOME.
     */
    public static File findStudioHome() {
        if (STUDIO_PROFILER_HOME != null) {
            File studioHome = new File(STUDIO_PROFILER_HOME);
            if (studioHome.exists()) {
                return studioHome;
            }
        }
        File applicationsDir = new File("/Applications");
        if (!applicationsDir.isDirectory()) {
            return null;
        }
        for (File file : applicationsDir.listFiles()) {
            System.out.println(file.getAbsolutePath());
            if (file.getName().matches("Android Studio.*\\.app")) {
                return file;
            }
        }
        return null;
    }

}
