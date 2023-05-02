package org.gradle.profiler.gradle;

import org.gradle.profiler.BuildAction;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.Timer;
import org.gradle.profiler.result.BuildActionResult;

import java.util.List;

public abstract class GradleInvokerBuildAction implements BuildAction {
    @Override
    public BuildActionResult run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
        Timer timer = new Timer();
        run((GradleInvoker) gradleClient, gradleArgs, jvmArgs);
        return new BuildActionResult(timer.elapsed());
    }

    protected abstract void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs);
}
