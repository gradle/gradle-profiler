package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.gradle.GradleScenarioDefinition;
import org.gradle.profiler.studio.IdeType;

import java.util.List;

public class IdeGradleScenarioDefinition extends GradleScenarioDefinition {

    public IdeGradleScenarioDefinition(GradleScenarioDefinition gradleScenarioDefinition, IdeType ideType, List<String> ideJvmArgs, List<String> ideaProperties) {
        super(
            gradleScenarioDefinition.getName(),
            gradleScenarioDefinition.getTitle(),
            gradleScenarioDefinition.getInvoker(),
            new IdeGradleBuildConfiguration(gradleScenarioDefinition.getBuildConfiguration(), ideType, ideJvmArgs, ideaProperties),
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

        private final IdeType ideType;
        private final List<String> ideJvmArgs;
        private final List<String> ideaProperties;

        IdeGradleBuildConfiguration(GradleBuildConfiguration gradleBuildConfiguration, IdeType ideType, List<String> ideJvmArguments, List<String> ideaProperties) {
            super(
                gradleBuildConfiguration.getGradleVersion(),
                gradleBuildConfiguration.getGradleHome(),
                gradleBuildConfiguration.getJavaHome(),
                gradleBuildConfiguration.getJvmArguments(),
                gradleBuildConfiguration.isUsesScanPlugin(),
                gradleBuildConfiguration.isUsesDevelocityPlugin()
            );
            this.ideType = ideType;
            this.ideJvmArgs = ideJvmArguments;
            this.ideaProperties = ideaProperties;
        }

        public IdeType getIdeType() {
            return ideType;
        }

        public List<String> getIdeJvmArgs() {
            return ideJvmArgs;
        }

        public List<String> getIdeaProperties() {
            return ideaProperties;
        }
    }
}
