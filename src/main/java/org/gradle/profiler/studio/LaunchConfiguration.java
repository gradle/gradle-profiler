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
    private final Path protocolJar;

    public LaunchConfiguration(Path javaCommand,
                               List<Path> classPath,
                               Map<String, String> systemProperties,
                               String mainClass,
                               Path agentJar,
                               Path supportJar,
                               Path protocolJar) {
        this.javaCommand = javaCommand;
        this.classPath = classPath;
        this.systemProperties = systemProperties;
        this.mainClass = mainClass;
        this.agentJar = agentJar;
        this.supportJar = supportJar;
        this.protocolJar = protocolJar;
    }

    public Path getJavaCommand() {
        return javaCommand;
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

    public Path getProtocolJar() {
        return protocolJar;
    }
}
