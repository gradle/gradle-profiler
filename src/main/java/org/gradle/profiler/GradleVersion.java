package org.gradle.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class GradleVersion {

    private static final String OS = System.getProperty("os.name");

    private final String version;
    private final File gradleHome;

    public GradleVersion(String version, File gradleHome) {
        this.version = version;
        this.gradleHome = gradleHome;
    }

    public String getVersion() {
        return version;
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
        if (isWindows()) {
            commandLine.add("cmd.exe");
            commandLine.add("/C");
        }
        commandLine.add(new File(gradleHome, "bin/gradle").getAbsolutePath());
    }

    private static boolean isWindows() {
        return OS.toLowerCase().startsWith("windows");
    }
}
