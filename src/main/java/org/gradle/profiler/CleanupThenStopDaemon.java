package org.gradle.profiler;

import java.util.List;

public class CleanupThenStopDaemon implements BuildAction {
    private final BuildAction cleanupAction;
    private final DaemonControl daemonControl;
    private final GradleBuildConfiguration configuration;

    public CleanupThenStopDaemon(BuildAction cleanupAction, DaemonControl daemonControl, GradleBuildConfiguration configuration) {
        this.cleanupAction = cleanupAction;
        this.daemonControl = daemonControl;
        this.configuration = configuration;
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public String getDisplayName() {
        if (cleanupAction.isDoesSomething()) {
            return cleanupAction.getDisplayName() + " then stop daemon";
        } else {
            return "stop daemon";
        }
    }

    @Override
    public String getShortDisplayName() {
        if (cleanupAction.isDoesSomething()) {
            return cleanupAction.getShortDisplayName() + " then stop daemon";
        } else {
            return "stop daemon";
        }
    }

    @Override
    public void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs) {
        cleanupAction.run(buildInvoker, gradleArgs, jvmArgs);
        daemonControl.stop(configuration);
    }
}
