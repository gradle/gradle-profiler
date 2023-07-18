package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.gradle.GradleScenarioDefinition;

import java.util.List;

public class StudioGradleScenarioDefinition extends GradleScenarioDefinition {

    public StudioGradleScenarioDefinition(GradleScenarioDefinition gradleScenarioDefinition, List<String> studioJvmArgs, List<String> ideaProperties) {
        super(
            gradleScenarioDefinition.getName(),
            gradleScenarioDefinition.getTitle(),
            gradleScenarioDefinition.getInvoker(),
            new StudioGradleBuildConfiguration(gradleScenarioDefinition.getBuildConfiguration(), studioJvmArgs, ideaProperties),
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
        private final List<String> ideaProperties;

        StudioGradleBuildConfiguration(GradleBuildConfiguration gradleBuildConfiguration, List<String> studioJvmArguments, List<String> ideaProperties) {
            super(
                gradleBuildConfiguration.getGradleVersion(),
                gradleBuildConfiguration.getGradleHome(),
                gradleBuildConfiguration.getJavaHome(),
                gradleBuildConfiguration.getJvmArguments(),
                gradleBuildConfiguration.isUsesScanPlugin()
            );
            this.studioJvmArgs = studioJvmArguments;
            this.ideaProperties = ideaProperties;
        }

        public List<String> getStudioJvmArgs() {
            return studioJvmArgs;
        }

        public List<String> getIdeaProperties() {
            return ideaProperties;
        }
    }
}
