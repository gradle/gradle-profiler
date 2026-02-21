package org.gradle.profiler.ide.invoker;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.gradle.GradleScenarioDefinition;

import java.util.List;

public class IdeGradleScenarioDefinition extends GradleScenarioDefinition {

    public IdeGradleScenarioDefinition(GradleScenarioDefinition gradleScenarioDefinition, List<String> ideJvmArgs, List<String> ideaProperties) {
        super(
            gradleScenarioDefinition.getName(),
            gradleScenarioDefinition.getTitle(),
            gradleScenarioDefinition.getInvoker(),
            new IdeGradleBuildConfiguration(gradleScenarioDefinition.getBuildConfiguration(), ideJvmArgs, ideaProperties),
            gradleScenarioDefinition.getAction(),
            gradleScenarioDefinition.getCleanupAction(),
            gradleScenarioDefinition.getGradleArgs(),
            gradleScenarioDefinition.getSystemProperties(),
            gradleScenarioDefinition.getBuildMutators(),
            gradleScenarioDefinition.getWarmUpCount(),
            gradleScenarioDefinition.getBuildCount(),
            gradleScenarioDefinition.getOutputDir(),
            gradleScenarioDefinition.getJvmArgs(),
            gradleScenarioDefinition.getBuildOperationMeasurements(),
            gradleScenarioDefinition.isBuildOperationsTrace()
        );
    }

    public static class IdeGradleBuildConfiguration extends GradleBuildConfiguration {

        private final List<String> ideJvmArgs;
        private final List<String> ideaProperties;

        IdeGradleBuildConfiguration(GradleBuildConfiguration gradleBuildConfiguration, List<String> ideJvmArgs, List<String> ideaProperties) {
            super(
                gradleBuildConfiguration.getGradleVersion(),
                gradleBuildConfiguration.getGradleHome(),
                gradleBuildConfiguration.getJavaHome(),
                gradleBuildConfiguration.getJvmArguments(),
                gradleBuildConfiguration.isUsesScanPlugin(),
                gradleBuildConfiguration.isUsesDevelocityPlugin()
            );
            this.ideJvmArgs = ideJvmArgs;
            this.ideaProperties = ideaProperties;
        }

        public List<String> getIdeJvmArgs() {
            return ideJvmArgs;
        }

        public List<String> getIdeaProperties() {
            return ideaProperties;
        }
    }
}
