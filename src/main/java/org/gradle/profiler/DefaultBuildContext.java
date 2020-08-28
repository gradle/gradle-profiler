package org.gradle.profiler;

public class DefaultBuildContext implements BuildContext {
    private final ScenarioContext scenarioContext;
    private final Phase phase;
    private final int iteration;

    protected DefaultBuildContext(ScenarioContext scenarioContext, Phase phase, int iteration) {
        this.scenarioContext = scenarioContext;
        this.phase = phase;
        this.iteration = iteration;
    }

    @Override
    public String getUniqueScenarioId() {
        return scenarioContext.getUniqueScenarioId();
    }

    @Override
    public BuildContext withBuild(Phase phase, int count) {
        return scenarioContext.withBuild(phase, count);
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
