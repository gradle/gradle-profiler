package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class ScenarioDefinition {
    private final String name;
    private final Supplier<BuildMutator> buildMutator;
    private final int warmUpCount;
    private final int buildCount;
    private final File outpuDir;

    public ScenarioDefinition(String name, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        this.name = name;
        this.buildMutator = buildMutator;
        this.warmUpCount = warmUpCount;
        this.buildCount = buildCount;
        this.outpuDir = outputDir;
    }

    /**
     * A human consumable and unique display name for this scenario.
     */
    public abstract String getDisplayName();

    /**
     * A unique name for this scenario, that can be used for file names and other identifiers.
     */
    public abstract String getProfileName();

    /**
     * A human consumable description of the build tool that runs this scenario.
     */
    public abstract String getBuildToolDisplayName();

    /**
     * A human consumable description of the 'tasks' that are run for this scenario (may not be Gradle tasks).
     */
    public abstract String getTasksDisplayName();

    public String getName() {
        return name;
    }

    public File getOutputDir() {
        return outpuDir;
    }

    public Supplier<BuildMutator> getBuildMutator() {
        return buildMutator;
    }

    public int getWarmUpCount() {
        return warmUpCount;
    }

    public int getBuildCount() {
        return buildCount;
    }

    public void printTo(PrintStream out) {
        out.println("Scenario: " + getDisplayName());
        printDetail(out);
        out.println("  Build changes: " + getBuildMutator());
        out.println("  Warm-ups: " + getWarmUpCount());
        out.println("  Builds: " + getBuildCount());
    }

    public void visitProblems(InvocationSettings settings, Consumer<String> reporter) {
    }

    protected void printDetail(PrintStream out) {
    }
}
