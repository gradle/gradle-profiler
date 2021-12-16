package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleScenarioDefinition;

import java.util.List;

public class StudioGradleScenarioDefinition extends GradleScenarioDefinition {

    public StudioGradleScenarioDefinition(GradleScenarioDefinition gradleScenarioDefinition, List<String> studioJvmArgs) {
        super(
            gradleScenarioDefinition.getName(),
            gradleScenarioDefinition.getTitle(),
            gradleScenarioDefinition.getInvoker(),
            new StudioGradleBuildConfiguration(gradleScenarioDefinition.getBuildConfiguration(), studioJvmArgs),
            gradleScenarioDefinition.getAction(),
            gradleScenarioDefinition.getCleanupAction(),
            gradleScenarioDefinition.getGradleArgs(),
            gradleScenarioDefinition.getSystemProperties(),
            gradleScenarioDefinition.getBuildMutators(),
            gradleScenarioDefinition.getWarmUpCount(),
            gradleScenarioDefinition.getBuildCount(),
            gradleScenarioDefinition.getOutputDir(),
            gradleScenarioDefinition.getJvmArgs(),
            gradleScenarioDefinition.getMeasuredBuildOperations()
        );
    }

    public static class StudioGradleBuildConfiguration extends GradleBuildConfiguration {

        private final List<String> studioJvmArgs;

        StudioGradleBuildConfiguration(GradleBuildConfiguration gradleBuildConfiguration, List<String> studioJvmArguments) {
            super(
                gradleBuildConfiguration.getGradleVersion(),
                gradleBuildConfiguration.getGradleHome(),
                gradleBuildConfiguration.getJavaHome(),
                gradleBuildConfiguration.getJvmArguments(),
                gradleBuildConfiguration.isUsesScanPlugin()
            );
            this.studioJvmArgs = studioJvmArguments;
        }

        public List<String> getStudioJvmArgs() {
            return studioJvmArgs;
        }
    }
}
