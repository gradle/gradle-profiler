package org.gradle.profiler;

import java.io.File;

public class DaemonControl {
    private final File gradleUserHome;

    public DaemonControl(File gradleUserHome) {
        this.gradleUserHome = gradleUserHome;
    }

    public void stop(GradleBuildConfiguration version) {
        Logging.startOperation("Stopping daemons");
        version.runGradle("--stop", "--gradle-user-home", gradleUserHome.getAbsolutePath());
    }
}
