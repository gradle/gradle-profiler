package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.VersionUtils;
import org.gradle.profiler.gradle.GradleScenarioDefinition;

import java.util.List;
import java.util.function.Consumer;

public class StudioGradleScenarioDefinition extends GradleScenarioDefinition {

    public StudioGradleScenarioDefinition(
        GradleScenarioDefinition gradleScenarioDefinition,
        List<String> studioJvmArgs,
        List<String> ideaProperties,
        String ideType,
        String ideVersion
    ) {
        super(
            gradleScenarioDefinition.getName(),
            gradleScenarioDefinition.getTitle(),
            gradleScenarioDefinition.getInvoker(),
            new StudioGradleBuildConfiguration(gradleScenarioDefinition.getBuildConfiguration(), studioJvmArgs, ideaProperties, ideType, ideVersion),
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

    @Override
    public void visitProblems(InvocationSettings settings, Consumer<String> reporter) {
        if (VersionUtils.getJavaVersion() < 17) {
            reporter.accept("Running IDE scenarios is only supported for Java 17 and later");
        }
        super.visitProblems(settings, reporter);
    }

    @Override
    public String getDisplayName() {
        return super.getDisplayName() + " and Java " + VersionUtils.getJavaVersion();
    }

    public static class StudioGradleBuildConfiguration extends GradleBuildConfiguration {

        private final List<String> studioJvmArgs;
        private final List<String> ideaProperties;
        private final String ideType;
        private final String ideVersion;

        StudioGradleBuildConfiguration(
            GradleBuildConfiguration gradleBuildConfiguration,
            List<String> studioJvmArguments,
            List<String> ideaProperties,
            String ideType,
            String ideVersion
        ) {
            super(
                gradleBuildConfiguration.getGradleVersion(),
                gradleBuildConfiguration.getGradleHome(),
                gradleBuildConfiguration.getJavaHome(),
                gradleBuildConfiguration.getJvmArguments(),
                gradleBuildConfiguration.isUsesScanPlugin()
            );
            this.studioJvmArgs = studioJvmArguments;
            this.ideaProperties = ideaProperties;
            this.ideType = ideType;
            this.ideVersion = ideVersion;
        }

        public List<String> getStudioJvmArgs() {
            return studioJvmArgs;
        }

        public List<String> getIdeaProperties() {
            return ideaProperties;
        }

        public String getIdeType() {
            return ideType;
        }

        public String getIdeVersion() {
            return ideVersion;
        }
    }
}
