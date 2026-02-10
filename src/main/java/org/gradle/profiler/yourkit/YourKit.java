package org.gradle.profiler.yourkit;

import org.gradle.profiler.OperatingSystem;

import java.io.File;

import static org.gradle.profiler.OperatingSystem.MAC_OS_APPLICATIONS_PATH;
import static org.gradle.profiler.OperatingSystem.MAC_OS_RESOURCES_PATH;

public class YourKit {
    static final String ENVIRONMENT_VARIABLE = "YOURKIT_HOME";
    private static final String YOURKIT_HOME = System.getenv(ENVIRONMENT_VARIABLE);

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
        File applicationsDir = new File(MAC_OS_APPLICATIONS_PATH);
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
        return tryLocations(yourKitHome, MAC_OS_RESOURCES_PATH + "/lib/yjp-controller-api-redist.jar", "lib/yjp-controller-api-redist.jar");
    }

    public static File findJniLib() {
        File yourKitHome = findYourKitHome();
        if (OperatingSystem.isWindows()) {
            return tryLocations(yourKitHome, "bin/win64/yjpagent.dll", "bin/windows-x86-64/yjpagent.dll");
        }
        String macLibLocationPrefix = MAC_OS_RESOURCES_PATH +"/bin/mac/libyjpagent.";
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
