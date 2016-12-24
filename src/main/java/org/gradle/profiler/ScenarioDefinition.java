package org.gradle.profiler;

import java.util.function.Supplier;

public abstract class ScenarioDefinition {
    private final String name;
    private final Supplier<BuildMutator> buildMutator;
    private final int warmUpCount;
    private final int buildCount;

    public ScenarioDefinition(String name, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount) {
        this.name = name;
        this.buildMutator = buildMutator;
        this.warmUpCount = warmUpCount;
        this.buildCount = buildCount;
    }

    public abstract String getDisplayName();

    public abstract String getShortDisplayName();

    public String getName() {
        return name;
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
}
