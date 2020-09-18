package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public class MavenScenarioDefinition extends BuildToolCommandLineScenarioDefinition {
    public MavenScenarioDefinition(
        String scenarioName,
        @Nullable String title,
        List<String> targets,
        Supplier<BuildMutator> buildMutator,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File mavenHome
    ) {
        super(scenarioName, title, targets, buildMutator, warmUpCount, buildCount, outputDir, mavenHome);
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
