package org.gradle.profiler;

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
