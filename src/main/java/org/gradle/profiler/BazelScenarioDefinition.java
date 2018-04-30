package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BazelScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;
    private final Version version;

    public BazelScenarioDefinition(String scenarioName, List<String> targets, Version version, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        super(scenarioName, buildMutator, warmUpCount, buildCount, outputDir);
        this.targets = targets;
        this.version = version;
    }

    @Override
    public String getDisplayName() {
        return getName() + " using Bazel " + version.getVersion();
    }

    @Override
    public String getProfileName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBuildToolDisplayName() {
        return version.getVersion();
    }

    @Override
    public String getTasksDisplayName() {
        return targets.stream().collect(Collectors.joining(" "));
    }

    public List<String> getTargets() {
        return targets;
    }

    @Override
    protected void printDetail(PrintStream out) {
        out.println("  Targets: " + getTargets());
    }
}
