package org.gradle.profiler.studio;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class LaunchConfiguration {
    private final Path javaCommand;
    private final List<Path> classPath;
    private final Map<String, String> systemProperties;
    private final String mainClass;
    private final Path agentJar;
    private final Path supportJar;
    private final List<Path> sharedJars;
    private final List<Path> studioPluginJars;
    private final Path studioPluginsDir;
    private final Path studioLogsDir;
    private final List<String> commandLine;
    private final Path studioInstallDir;

    public LaunchConfiguration(Path javaCommand,
                               Path studioInstallDir,
                               List<Path> classPath,
                               Map<String, String> systemProperties,
                               String mainClass,
                               Path agentJar,
                               Path supportJar,
                               List<Path> sharedJars,
                               List<Path> studioPluginJars,
                               Path studioPluginsDir,
                               Path studioLogsDir,
                               List<String> commandLine) {
        this.javaCommand = javaCommand;
        this.studioInstallDir = studioInstallDir;
        this.classPath = classPath;
        this.systemProperties = systemProperties;
        this.mainClass = mainClass;
        this.agentJar = agentJar;
        this.supportJar = supportJar;
        this.sharedJars = sharedJars;
        this.studioPluginJars = studioPluginJars;
        this.studioPluginsDir = studioPluginsDir;
        this.studioLogsDir = studioLogsDir;
        this.commandLine = commandLine;
    }

    public Path getJavaCommand() {
        return javaCommand;
    }

    public Path studioInstallDir() {
        return studioInstallDir;
    }

    public List<Path> getClassPath() {
        return classPath;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public String getMainClass() {
        return mainClass;
    }

    public Path getAgentJar() {
        return agentJar;
    }

    public Path getSupportJar() {
        return supportJar;
    }

    public List<Path> getSharedJars() {
        return sharedJars;
    }

    public List<Path> getStudioPluginJars() {
        return studioPluginJars;
    }

    public Path getStudioPluginsDir() {
        return studioPluginsDir;
    }

    public Path getStudioLogsDir() {
        return studioLogsDir;
    }

    public List<String> getCommandLine() {
        return commandLine;
    }
}
