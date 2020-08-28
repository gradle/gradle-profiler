package org.gradle.profiler;

import java.util.UUID;

public class DefaultBuildContext extends DefaultScenarioContext implements BuildContext {
    private final Phase phase;
    private final int iteration;

    protected DefaultBuildContext(UUID invocationId, String scenarioName, Phase phase, int iteration) {
        super(invocationId, scenarioName);
        this.phase = phase;
        this.iteration = iteration;
    }

    @Override
    public String getUniqueBuildId() {
        return String.format("%s_%s_%d", getUniqueScenarioId(), phase.name(), iteration);
    }

    @Override
    public Phase getPhase() {
        return phase;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    @Override
    public String getDisplayName() {
        return phase.displayBuildNumber(iteration);
    }
}
