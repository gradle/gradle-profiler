package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class BazelScenarioDefinition extends BuildToolCommandLineScenarioDefinition {
    public BazelScenarioDefinition(
        String scenarioName,
        @Nullable String title,
        List<String> targets,
        List<BuildMutator> buildMutators,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File bazelHome
    ) {
        super(scenarioName, title, targets, buildMutators, warmUpCount, buildCount, outputDir, bazelHome);
    }

    @Override
    public String getDisplayName() {
        return getTitle() + " using bazel";
    }

    @Override
    public String getProfileName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBuildToolDisplayName() {
        return "bazel";
    }

    @Override
    public BuildInvoker getInvoker() {
        return BuildInvoker.Bazel;
    }

    @Override
    protected String getExecutableName() {
        if (OperatingSystem.isWindows()) {
            return "bazel.exe";
        } else {
            return "bazel";
        }
    }

    @Override
    protected String getToolHomeEnvName() {
        return "BAZEL_HOME";
    }
}
