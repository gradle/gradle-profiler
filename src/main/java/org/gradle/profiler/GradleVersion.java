package org.gradle.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class GradleVersion extends Version {

    private final File gradleHome;

    public GradleVersion(String version, File gradleHome) {
        super(version);
        this.gradleHome = gradleHome;
    }

    public File getGradleHome() {
        return gradleHome;
    }

    public void runGradle(String... arguments) {
        List<String> commandLine = new ArrayList<>();
        addGradleCommand(commandLine);
        commandLine.addAll(Arrays.asList(arguments));
        new CommandExec().run(commandLine);
    }

    public void addGradleCommand(List<String> commandLine) {
        if (OperatingSystem.isWindows()) {
            commandLine.add("cmd.exe");
            commandLine.add("/C");
        }
        commandLine.add(new File(gradleHome, "bin/gradle").getAbsolutePath());
    }
}
