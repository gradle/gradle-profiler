package org.gradle.profiler;

public class ScenarioSettings {
    private InvocationSettings invocationSettings;
    private final ScenarioDefinition scenario;

    public ScenarioSettings(InvocationSettings invocationSettings, ScenarioDefinition scenario) {
        this.invocationSettings = invocationSettings;
        this.scenario = scenario;
    }

    public InvocationSettings getInvocationSettings() {
        return invocationSettings;
    }

    public ScenarioDefinition getScenario() {
        return scenario;
    }
}
