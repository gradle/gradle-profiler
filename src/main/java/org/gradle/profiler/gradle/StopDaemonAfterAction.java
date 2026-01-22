package org.gradle.profiler.gradle;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildStep;
import org.gradle.profiler.BuildStepAction;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.gradle.DaemonControl;
import org.gradle.profiler.result.BuildInvocationResult;

public class StopDaemonAfterAction<T extends BuildInvocationResult> implements BuildStepAction<T> {
    private final BuildStepAction<T> action;
    private final DaemonControl daemonControl;
    private final GradleBuildConfiguration configuration;

    public StopDaemonAfterAction(BuildStepAction<T> action, DaemonControl daemonControl, GradleBuildConfiguration configuration) {
        this.action = action;
        this.daemonControl = daemonControl;
        this.configuration = configuration;
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public T run(BuildContext buildContext, BuildStep buildStep) {
        T result = action.run(buildContext, buildStep);
        daemonControl.stop(configuration);
        return result;
    }
}
