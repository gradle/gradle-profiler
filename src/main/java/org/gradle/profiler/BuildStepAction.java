package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

public interface BuildStepAction<T extends BuildInvocationResult> {
    boolean isDoesSomething();

    /**
     * Runs a single build step.
     */
    T run(BuildContext buildContext, BuildStep buildStep);
}
