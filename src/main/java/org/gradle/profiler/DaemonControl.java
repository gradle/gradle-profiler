package org.gradle.profiler;

import java.io.File;

public class DaemonControl {
    public void stop(GradleVersion version) {
        new CommandExec().run(new File(version.getGradleHome(), "bin/gradle").getAbsolutePath(), "--stop");
    }
}
