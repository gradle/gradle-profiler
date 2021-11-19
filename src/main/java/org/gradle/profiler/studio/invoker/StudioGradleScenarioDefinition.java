package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.ScenarioDefinition;

public class StudioGradleScenarioDefinition extends ScenarioDefinition {

    private final GradleScenarioDefinition gradleScenarioDefinition;

    public StudioGradleScenarioDefinition(GradleScenarioDefinition gradleScenarioDefinition) {
        super(
            gradleScenarioDefinition.getName(),
            gradleScenarioDefinition.getTitle(),
            gradleScenarioDefinition.getBuildMutators(),
            gradleScenarioDefinition.getWarmUpCount(),
            gradleScenarioDefinition.getBuildCount(),
            gradleScenarioDefinition.getOutputDir()
        );
        this.gradleScenarioDefinition = gradleScenarioDefinition;
    }

    public GradleScenarioDefinition getGradleScenarioDefinition() {
        return gradleScenarioDefinition;
    }

    @Override
    public String getDisplayName() {
        return gradleScenarioDefinition.getDisplayName();
    }

    @Override
    public String getProfileName() {
        return gradleScenarioDefinition.getProfileName();
    }

    @Override
    public String getBuildToolDisplayName() {
        return gradleScenarioDefinition.getBuildToolDisplayName();
    }

    @Override
    public String getTasksDisplayName() {
        return gradleScenarioDefinition.getTasksDisplayName();
    }

    @Override
    public BuildInvoker getInvoker() {
        return gradleScenarioDefinition.getInvoker();
    }
}
