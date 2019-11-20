package org.gradle.profiler;

import java.util.UUID;

public class ScenarioContext {
    private final UUID invocationId;
    private final String scenarioName;

    public static ScenarioContext from(InvocationSettings invocationSettings, ScenarioDefinition scenarioDefinition) {
        return new ScenarioContext(invocationSettings.getInvocationId(), scenarioDefinition.getName());
    };

    protected ScenarioContext(UUID invocationId, String scenarioName) {
        this.invocationId = invocationId;
        this.scenarioName = scenarioName;
    }

    public String getUniqueScenarioId() {
        return String.format("%s-%s", invocationId, scenarioName);
    }

    public BuildContext withBuild(Phase phase, int count) {
        return new BuildContext(invocationId, scenarioName, phase, count);
    }
}
