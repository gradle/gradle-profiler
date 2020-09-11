package org.gradle.profiler;

import java.time.Duration;
import java.util.List;

public abstract class GradleInvokerBuildAction implements BuildAction {
    @Override
    public Duration run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
        Timer timer = new Timer();
        run((GradleInvoker) gradleClient, gradleArgs, jvmArgs);
        return timer.elapsed();
    }

    protected abstract void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs);
}
