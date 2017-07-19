package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;

public class MavenScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;

    public MavenScenarioDefinition(String scenarioName, List<String> targets, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        super(scenarioName, buildMutator, warmUpCount, buildCount, outputDir);
        this.targets = targets;
    }

    @Override
    public String getShortDisplayName() {
        return getName() + " maven";
    }

    @Override
    public String getDisplayName() {
        return getName() + " using maven";
    }

    @Override
    public String getProfileName() {
        throw new UnsupportedOperationException();
    }

    public List<String> getTargets() {
        return targets;
    }

    @Override
    protected void printDetail(PrintStream out) {
        out.println("  Targets: " + getTargets());
    }
}
