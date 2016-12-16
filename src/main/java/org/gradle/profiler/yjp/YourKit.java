package org.gradle.profiler.yjp;

import java.io.File;

public class YourKit {
    /**
     * Locates the user's YourKit installation. Returns null when not found.
     */
    public static File findYourKitHome() {
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
}
