package org.gradle.profiler;

import java.util.List;

public class RunTasksAction implements BuildAction {
    @Override
    public String getDisplayName() {
        return "run tasks";
    }

    @Override
    public void run(BuildInvoker buildInvoker, List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        buildInvoker.runTasks(tasks, gradleArgs, jvmArgs);
    }
}
