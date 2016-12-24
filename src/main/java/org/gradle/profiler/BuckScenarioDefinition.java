package org.gradle.profiler;

import java.util.function.Supplier;

public class BuckScenarioDefinition extends ScenarioDefinition {

    public BuckScenarioDefinition(String name, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount) {
        super(name, buildMutator, warmUpCount, buildCount);
    }

    @Override
    public String getShortDisplayName() {
        return getName() + " buck";
    }

    @Override
    public String getDisplayName() {
        return getName() + " using buck";
    }
}
