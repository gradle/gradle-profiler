package org.gradle.profiler;

public class StopDaemonAfterAction implements BuildStepAction {
    private final BuildStepAction action;
    private final DaemonControl daemonControl;
    private final GradleBuildConfiguration configuration;

    public StopDaemonAfterAction(BuildStepAction action, DaemonControl daemonControl, GradleBuildConfiguration configuration) {
        this.action = action;
        this.daemonControl = daemonControl;
        this.configuration = configuration;
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public GradleBuildInvocationResult run(BuildContext buildContext, BuildStep buildStep) {
        GradleBuildInvocationResult result = action.run(buildContext, buildStep);
        daemonControl.stop(configuration);
        return result;
    }
}
