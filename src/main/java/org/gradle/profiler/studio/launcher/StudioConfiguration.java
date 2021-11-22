package org.gradle.profiler.studio.launcher;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class StudioConfiguration {

    private final String mainClass;
    private final Path actualInstallDir;
    private final Path javaCommand;
    private final List<Path> classpath;
    private final Map<String, String> systemProperties;

    public StudioConfiguration(String mainClass, Path actualInstallDir, Path javaCommand, List<Path> classpath, Map<String, String> systemProperties) {
        this.mainClass = mainClass;
        this.actualInstallDir = actualInstallDir;
        this.javaCommand = javaCommand;
        this.classpath = classpath;
        this.systemProperties = systemProperties;
    }

    public String getMainClass() {
        return mainClass;
    }

    public Path getActualInstallDir() {
        return actualInstallDir;
    }

    public Path getJavaCommand() {
        return javaCommand;
    }

    public List<Path> getClasspath() {
        return classpath;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }
}
