package org.gradle.profiler;

public interface ScenarioContext {
    static ScenarioContext from(InvocationSettings invocationSettings, ScenarioDefinition scenarioDefinition) {
        return new DefaultScenarioContext(invocationSettings.getInvocationId(), scenarioDefinition.getName());
    }

    String getUniqueScenarioId();

    BuildContext withBuild(Phase phase, int count);
}
