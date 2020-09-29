package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class MavenScenarioDefinition extends BuildToolCommandLineScenarioDefinition {
    public MavenScenarioDefinition(
        String scenarioName,
        @Nullable String title,
        List<String> targets,
        List<BuildMutator> buildMutators,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File mavenHome
    ) {
        super(scenarioName, title, targets, buildMutators, warmUpCount, buildCount, outputDir, mavenHome);
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
}
