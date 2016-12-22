package org.gradle.profiler;

public class DaemonControl {
    public void stop(GradleVersion version) {
        version.runGradle("--stop");
    }
}
