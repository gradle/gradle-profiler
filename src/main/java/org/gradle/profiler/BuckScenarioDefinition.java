package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BuckScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;
    private final String type;

    public BuckScenarioDefinition(String scenarioName, String title, List<String> targets, String type, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        super(scenarioName, title, buildMutator, warmUpCount, buildCount, outputDir);
        this.targets = targets;
        this.type = type;
    }

    @Override
    public String getDisplayName() {
        return getTitle() + " using buck";
    }

    @Override
    public String getProfileName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBuildToolDisplayName() {
        return "buck";
    }

    @Override
    public String getTasksDisplayName() {
        return targets.stream().collect(Collectors.joining(" "));
    }

    public List<String> getTargets() {
        return targets;
    }

    public String getType() {
        return type;
    }

    @Override
    protected void printDetail(PrintStream out) {
        out.println("  Targets: " + getTargets());
        if (getType() != null) {
            out.println("  Type: " + getType());
        }
    }
}
