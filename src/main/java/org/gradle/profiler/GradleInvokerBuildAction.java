package org.gradle.profiler;

import java.util.List;

public abstract class GradleInvokerBuildAction implements BuildAction {
    @Override
    public BuildActionResult run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
        Timer timer = new Timer();
        run((GradleInvoker) gradleClient, gradleArgs, jvmArgs);
        return BuildActionResult.withExecutionTimeOnly(timer.elapsed());
    }

    protected abstract void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs);
}
