package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

public interface BuildStepAction<T extends BuildInvocationResult> {
    BuildStepAction<GradleBuildInvocationResult> NO_OP = new BuildStepAction<GradleBuildInvocationResult>() {
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
    T run(BuildContext buildContext, BuildStep buildStep);
}
