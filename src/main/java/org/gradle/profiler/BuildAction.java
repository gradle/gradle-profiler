package org.gradle.profiler;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

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
            return BuildActionResult.of(Duration.ZERO);
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
        private final Duration studioExecutionTime;

        private BuildActionResult(Duration executionTime, @Nullable Duration gradleToolingAgentExecutionTime, @Nullable Duration studioExecutionTime) {
            this.executionTime = executionTime;
            this.gradleToolingAgentExecutionTime = gradleToolingAgentExecutionTime;
            this.studioExecutionTime = studioExecutionTime;
        }

        public Duration getExecutionTime() {
            return executionTime;
        }

        public Duration getGradleToolingAgentExecutionTime() {
            return gradleToolingAgentExecutionTime;
        }

        public Duration getStudioExecutionTime() {
            return studioExecutionTime;
        }

        public static BuildActionResult of(Duration executionTime) {
            return new BuildActionResult(executionTime, null, null);
        }

        public static BuildActionResult of(Duration executionTime, @Nullable Duration gradleToolingAgentExecutionTime, @Nullable Duration studioExecutionTime) {
            return new BuildActionResult(executionTime, gradleToolingAgentExecutionTime, studioExecutionTime);
        }
    }

}
