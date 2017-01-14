package org.gradle.profiler;

public class ScenarioSettings {
    private final InvocationSettings invocationSettings;
    private final GradleScenarioDefinition scenario;

    public ScenarioSettings(InvocationSettings invocationSettings, GradleScenarioDefinition scenario) {
        this.invocationSettings = invocationSettings;
        this.scenario = scenario;
    }

    public InvocationSettings getInvocationSettings() {
        return invocationSettings;
    }

    public GradleScenarioDefinition getScenario() {
        return scenario;
    }
}
