package org.gradle.profiler;

import java.util.List;

public class LoadToolingModelAction implements BuildAction {
    private final Class<?> toolingModel;

    public LoadToolingModelAction(Class<?> toolingModel) {
        this.toolingModel = toolingModel;
    }

    @Override
    public String getDisplayName() {
        return "load tooling model " + toolingModel.getSimpleName();
    }

    @Override
    public void run(BuildInvoker buildInvoker, List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        buildInvoker.loadToolingModel(tasks, gradleArgs, jvmArgs, toolingModel);
    }
}
