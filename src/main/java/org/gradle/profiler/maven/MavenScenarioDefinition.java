package org.gradle.profiler.maven;

import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.BuildToolCommandLineScenarioDefinition;
import org.gradle.profiler.OperatingSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;

public class MavenScenarioDefinition extends BuildToolCommandLineScenarioDefinition {
    private final Map<String, String> systemProperties;

    public MavenScenarioDefinition(
        String scenarioName,
        @Nullable String title,
        List<String> targets,
        Map<String, String> systemProperties,
        List<BuildMutator> buildMutators,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File mavenHome
    ) {
        super(scenarioName, title, targets, buildMutators, warmUpCount, buildCount, outputDir, mavenHome);
        this.systemProperties = systemProperties;
    }

    @Override
    public String getDisplayName() {
        return getTitle() + " using maven";
    }

    @Override
    public String getProfileName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBuildToolDisplayName() {
        return "maven";
    }

    @Override
    public BuildInvoker getInvoker() {
        return BuildInvoker.Maven;
    }

    @Override
    protected String getExecutableName() {
        if (OperatingSystem.isWindows()) {
            return "mvn.cmd";
        } else {
            return "mvn";
        }
    }

    @Override
    protected String getToolHomeEnvName() {
        return "MAVEN_HOME";
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }
}
