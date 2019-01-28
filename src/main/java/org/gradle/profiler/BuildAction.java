package org.gradle.profiler;

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
        public void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs) {
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
     * Runs the measured work of this action.
     */
    void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs);
}
