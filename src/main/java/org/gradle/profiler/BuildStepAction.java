package org.gradle.profiler;

public interface BuildStepAction {
    BuildStepAction NO_OP = new BuildStepAction() {
        @Override
        public boolean isDoesSomething() {
            return false;
        }

        @Override
        public GradleBuildInvocationResult run(BuildContext buildContext, BuildStep buildStep) {
            return null;
        }
    };

    boolean isDoesSomething();

    /**
     * Runs a single build step.
     */
    GradleBuildInvocationResult run(BuildContext buildContext, BuildStep buildStep);
}
