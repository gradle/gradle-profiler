package org.gradle.profiler.yourkit;

import java.io.File;

public class YourKit {
    static final String ENIVONMENT_VARIABLE = "YOURKIT_HOME";
    private static final String YOURKIT_HOME = System.getenv(ENIVONMENT_VARIABLE);

    /**
     * Locates the user's YourKit installation. Returns null when not found.
     */
    public static File findYourKitHome() {
        if (YOURKIT_HOME != null) {
            File ykHome = new File(YOURKIT_HOME);
            if (ykHome.exists()) {
                return ykHome;
            }
        }
        File applicationsDir = new File("/Applications");
        if (!applicationsDir.isDirectory()) {
            return null;
        }
        for (File file : applicationsDir.listFiles()) {
            if (file.getName().matches("YourKit.*\\.app")) {
                return file;
            }
        }
        return null;
    }

    public static File findControllerJar() {
        File yourKitHome = findYourKitHome();
        return tryLocations(yourKitHome, "Contents/Resources/lib/yjp-controller-api-redist.jar", "lib/yjp-controller-api-redist.jar");
    }

    public static File findJniLib() {
        File yourKitHome = findYourKitHome();
        String macLibLocationPrefix = "Contents/Resources/bin/mac/libyjpagent.";
        return tryLocations(yourKitHome, macLibLocationPrefix + "jnilib", macLibLocationPrefix + "dylib", "bin/linux-x86-64/libyjpagent.so");
    }

    private static File tryLocations(File baseDir, String... candidates) {
        for (String candidate : candidates) {
            File location = new File(baseDir, candidate);
            if (location.exists()) {
                return location;
            }
        }
        return null;
    }
}
