package org.gradle.profiler.gradle;

import org.gradle.tooling.BuildAction;

import java.util.List;

public class RunToolingAction extends GradleInvokerBuildAction {
    private final BuildAction<?> action;
    private final List<String> tasks;

    public RunToolingAction(BuildAction<?> action, List<String> tasks) {
        this.action = action;
        this.tasks = tasks;
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public String getShortDisplayName() {
        return "action " + action.getClass().getSimpleName();
    }

    @Override
    public String getDisplayName() {
        return "run tooling action " + action.getClass().getSimpleName();
    }

    @Override
    public void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs) {
        buildInvoker.runToolingAction(tasks, gradleArgs, jvmArgs, action, executer -> {});
    }
}
