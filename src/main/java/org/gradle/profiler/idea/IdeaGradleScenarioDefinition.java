package org.gradle.profiler.idea;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.gradle.GradleScenarioDefinition;

public class IdeaGradleScenarioDefinition extends GradleScenarioDefinition {

    public IdeaGradleScenarioDefinition(
        GradleScenarioDefinition gradleScenarioDefinition,
        IdeaSyncScenarioDefinition ideaSyncScenarioDefinition
    ) {
        super(
            gradleScenarioDefinition.getName(),
            gradleScenarioDefinition.getTitle(),
            gradleScenarioDefinition.getInvoker(),
            new IdeaGradleScenarioDefinition.IdeaGradleBuildConfiguration(gradleScenarioDefinition.getBuildConfiguration(), ideaSyncScenarioDefinition),
            gradleScenarioDefinition.getAction(),
            gradleScenarioDefinition.getCleanupAction(),
            gradleScenarioDefinition.getGradleArgs(),
            gradleScenarioDefinition.getSystemProperties(),
            gradleScenarioDefinition.getBuildMutators(),
            gradleScenarioDefinition.getWarmUpCount(),
            gradleScenarioDefinition.getBuildCount(),
            gradleScenarioDefinition.getOutputDir(),
            gradleScenarioDefinition.getJvmArgs(),
            gradleScenarioDefinition.getMeasuredBuildOperations(),
            gradleScenarioDefinition.isBuildOperationsTrace()
        );
    }

    public static class IdeaGradleBuildConfiguration extends GradleBuildConfiguration {
        private final IdeaSyncScenarioDefinition ideaSyncScenarioDefinition;

        IdeaGradleBuildConfiguration(GradleBuildConfiguration gradleBuildConfiguration, IdeaSyncScenarioDefinition ideaSyncScenarioDefinition) {
            super(
                gradleBuildConfiguration.getGradleVersion(),
                gradleBuildConfiguration.getGradleHome(),
                gradleBuildConfiguration.getJavaHome(),
                gradleBuildConfiguration.getJvmArguments(),
                gradleBuildConfiguration.isUsesScanPlugin(),
                gradleBuildConfiguration.isUsesDevelocityPlugin()
            );

            this.ideaSyncScenarioDefinition = ideaSyncScenarioDefinition;
        }

        public IdeaSyncScenarioDefinition getIdeaSyncScenarioDefinition() {
            return ideaSyncScenarioDefinition;
        }
    }
}
