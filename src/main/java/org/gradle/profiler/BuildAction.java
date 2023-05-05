package org.gradle.profiler;

import org.gradle.profiler.result.BuildActionResult;

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
            return new BuildActionResult(Duration.ZERO);
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

}
