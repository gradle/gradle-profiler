package org.gradle.profiler;

import java.util.UUID;

public class BuildContext extends ScenarioContext {
    private final Phase phase;
    private final int iteration;

    protected BuildContext(UUID invocationId, String scenarioName, Phase phase, int iteration) {
        super(invocationId, scenarioName);
        this.phase = phase;
        this.iteration = iteration;
    }

    public String getUniqueBuildId() {
        return String.format("%s_%s_%d", getUniqueScenarioId(), phase.name(), iteration);
    }

    public Phase getPhase() {
        return phase;
    }

    public int getIteration() {
        return iteration;
    }

    public String getDisplayName() {
        return phase.displayBuildNumber(iteration);
    }
}
