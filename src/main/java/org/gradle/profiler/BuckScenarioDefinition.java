package org.gradle.profiler;

import java.util.List;
import java.util.function.Supplier;

public class BuckScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;
    private final String type;

    public BuckScenarioDefinition(String scenarioName, List<String> targets, String type, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount) {
        super(scenarioName, buildMutator, warmUpCount, buildCount);
        this.targets = targets;
        this.type = type;
    }

    @Override
    public String getShortDisplayName() {
        return getName() + " buck";
    }

    @Override
    public String getDisplayName() {
        return getName() + " using buck";
    }

    public List<String> getTargets() {
        return targets;
    }

    public String getType() {
        return type;
    }
}
