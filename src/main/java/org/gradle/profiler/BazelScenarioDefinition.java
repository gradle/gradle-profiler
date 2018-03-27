package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BazelScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;

    public BazelScenarioDefinition(String scenarioName, List<String> targets, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        super(scenarioName, buildMutator, warmUpCount, buildCount, outputDir);
        this.targets = targets;
    }

    @Override
    public String getDisplayName() {
        return getName() + " using bazel";
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
