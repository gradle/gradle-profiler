package org.gradle.profiler;

import java.io.File;

public interface ScenarioContext {
    static ScenarioContext from(InvocationSettings invocationSettings, ScenarioDefinition scenarioDefinition) {
        return new DefaultScenarioContext(invocationSettings.getInvocationId(), scenarioDefinition.getName(), invocationSettings.getProjectDir());
    }

    String getUniqueScenarioId();

    File getProjectDir();

    BuildContext withBuild(Phase phase, int count);
}
