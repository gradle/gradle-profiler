package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public class BazelScenarioDefinition extends BuildToolCommandLineScenarioDefinition {
    public BazelScenarioDefinition(
        String scenarioName,
        String title,
        List<String> targets,
        Supplier<BuildMutator> buildMutator,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File bazelHome
    ) {
        super(scenarioName, title, targets, buildMutator, warmUpCount, buildCount, outputDir, bazelHome);
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
