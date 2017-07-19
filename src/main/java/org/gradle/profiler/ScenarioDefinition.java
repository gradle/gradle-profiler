package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
import java.util.function.Supplier;

public abstract class ScenarioDefinition {
    private final String name;
    private final Supplier<BuildMutator> buildMutator;
    private final int warmUpCount;
    private final int buildCount;
    private final File outpuDir;

    public ScenarioDefinition(String name, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outpuDir) {
        this.name = name;
        this.buildMutator = buildMutator;
        this.warmUpCount = warmUpCount;
        this.buildCount = buildCount;
        this.outpuDir = outpuDir;
    }

    public abstract String getDisplayName();

    public abstract String getShortDisplayName();

    public abstract String getProfileName();

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

    protected void printDetail(PrintStream out) {
    }
}
