package org.gradle.profiler;

import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GradleBuildConfiguration {
    private final GradleVersion gradleVersion;
    private final File gradleHome;
    private final File javaHome;
    private final List<String> jvmArguments;
    private final boolean usesScanPlugin;

    public GradleBuildConfiguration(GradleVersion gradleVersion, File gradleHome, File javaHome, List<String> jvmArguments, boolean usesScanPlugin) {
        this.gradleVersion = gradleVersion;
        this.gradleHome = gradleHome;
        this.javaHome = javaHome;
        this.usesScanPlugin = usesScanPlugin;
        this.jvmArguments = jvmArguments;
    }

    public GradleVersion getGradleVersion() {
        return gradleVersion;
    }

    public File getGradleHome() {
        return gradleHome;
    }

    public File getJavaHome() {
        return javaHome;
    }

    public List<String> getJvmArguments() {
        return jvmArguments;
    }

    public boolean isUsesScanPlugin() {
        return usesScanPlugin;
    }

    public void printVersionInfo() {
        Logging.detailed().println();
        Logging.detailed().println("* Build details");
        Logging.detailed().println("Gradle version: " + gradleVersion);

        Logging.detailed().println("Java home: " + javaHome);
        Logging.detailed().println("OS: " + OperatingSystem.getId());
    }

    public void runGradle(String... arguments) {
        List<String> commandLine = new ArrayList<>();
        addGradleCommand(commandLine);
        commandLine.addAll(Arrays.asList(arguments));
        new CommandExec().run(commandLine);
    }

    public void addGradleCommand(List<String> commandLine) {
        String gradleScriptName = OperatingSystem.isWindows()
            ? "gradle.bat"
            : "gradle";
        commandLine.add(new File(gradleHome, "bin/" + gradleScriptName).getAbsolutePath());
    }
}
