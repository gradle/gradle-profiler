package org.gradle.profiler;

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
        public Duration run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
            return Duration.ZERO;
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
    Duration run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs);
}
