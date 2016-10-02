package net.rubygrapefruit.gradle.profiler;

import java.io.File;
import java.io.IOException;

public class DaemonControl {
    public void stop(GradleVersion version) throws IOException, InterruptedException {
        int result = new ProcessBuilder(new File(version.getGradleHome(), "bin/gradle").getAbsolutePath(), "--stop").start().waitFor();
        if (result != 0) {
            throw new RuntimeException("Could not stop daemons for Gradle version " + version.getVersion());
        }
    }
}
