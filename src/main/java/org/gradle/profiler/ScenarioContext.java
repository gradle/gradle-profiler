package org.gradle.profiler;

import com.google.common.annotations.VisibleForTesting;

import java.util.UUID;

public class ScenarioContext {
    private final UUID invocationId;
    private final String scenarioName;

    public static ScenarioContext from(InvocationSettings invocationSettings, ScenarioDefinition scenarioDefinition) {
        return new ScenarioContext(invocationSettings.getInvocationId(), scenarioDefinition.getName());
    };

    @VisibleForTesting
    public ScenarioContext(UUID invocationId, String scenarioName) {
        this.invocationId = invocationId;
        this.scenarioName = scenarioName;
    }

    public String getUniqueScenarioId() {
        return String.format("_%s_%s", invocationId.toString().replaceAll("-", "_"), scenarioName);
    }

    public BuildContext withBuild(Phase phase, int count) {
        return new BuildContext(invocationId, scenarioName, phase, count);
    }
}
