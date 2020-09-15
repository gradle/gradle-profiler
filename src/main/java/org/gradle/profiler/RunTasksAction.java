package org.gradle.profiler;

import java.util.List;
import java.util.stream.Collectors;

public class RunTasksAction extends GradleInvokerBuildAction {
    private final List<String> tasks;

    public RunTasksAction(List<String> tasks) {
        this.tasks = tasks;
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public String getShortDisplayName() {
        if (tasks.isEmpty()) {
            return "default tasks";
        }
        return tasks.stream().collect(Collectors.joining(" "));
    }

    @Override
    public String getDisplayName() {
        if (tasks.isEmpty()) {
            return "run default tasks";
        }
        return "run tasks " + getShortDisplayName();
    }

    @Override
    public void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs) {
        buildInvoker.runTasks(tasks, gradleArgs, jvmArgs);
    }
}
