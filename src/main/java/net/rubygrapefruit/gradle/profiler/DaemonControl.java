package net.rubygrapefruit.gradle.profiler;

import java.io.File;

public class DaemonControl {
    public void stop(GradleVersion version) {
        try {
            int result = new ProcessBuilder(new File(version.getGradleHome(), "bin/gradle").getAbsolutePath(), "--stop").start().waitFor();
            if (result != 0) {
                throw new RuntimeException("Gradle stop command completed with non-zero status code.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not stop daemons for Gradle version " + version.getVersion(), e);
        }
    }
}
