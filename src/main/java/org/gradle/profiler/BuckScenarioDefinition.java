package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BuckScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;
    private final String type;
    private final Version version;

    public BuckScenarioDefinition(String scenarioName, List<String> targets, String type, Version version, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        super(scenarioName, buildMutator, warmUpCount, buildCount, outputDir);
        this.targets = targets;
        this.type = type;
        this.version = version;
    }

    @Override
    public String getDisplayName() {
        return getName() + " using Buck " + version.getVersion();
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
