package org.gradle.profiler;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Runs some particular action against a Gradle build.
 */
public interface BuildAction {
    BuildAction NO_OP = new BuildAction() {
        @Override
        public boolean isDoesSomething() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return "do nothing";
        }

        @Override
        public String getShortDisplayName() {
            return "nothing";
        }

        @Override
        public BuildActionResult run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
            return BuildActionResult.withExecutionTimeOnly(Duration.ZERO);
        }
    };

    boolean isDoesSomething();

    /**
     * A human consumable display name for this action.
     */
    String getDisplayName();

    /**
     * A human consumable display name for this action.
     */
    String getShortDisplayName();

    /**
     * Runs the work of this action and returns the result.
     */
    BuildActionResult run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs);

    class BuildActionResult {

        private final Duration executionTime;
        private final Duration gradleToolingAgentExecutionTime;
        private final Duration ideExecutionTime;

        private BuildActionResult(Duration executionTime, Duration gradleToolingAgentExecutionTime, Duration ideExecutionTime) {
            this.executionTime = executionTime;
            this.gradleToolingAgentExecutionTime = gradleToolingAgentExecutionTime;
            this.ideExecutionTime = ideExecutionTime;
        }

        public Duration getExecutionTime() {
            return executionTime;
        }

        public Duration getGradleToolingAgentExecutionTime() {
            return gradleToolingAgentExecutionTime;
        }

        public Duration getIdeExecutionTime() {
            return ideExecutionTime;
        }

        public static BuildActionResult withExecutionTimeOnly(Duration executionTime) {
            return new BuildActionResult(executionTime, Duration.ZERO, Duration.ZERO);
        }

        public static BuildActionResult withIdeTimings(Duration executionTime, Duration gradleToolingAgentExecutionTime, Duration ideExecutionTime) {
            return new BuildActionResult(executionTime, gradleToolingAgentExecutionTime, ideExecutionTime);
        }
    }

}
