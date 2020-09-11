package org.gradle.profiler;

import java.util.List;

public class LoadToolingModelAction extends GradleInvokerBuildAction {
    private final Class<?> toolingModel;
    private final List<String> tasks;

    public LoadToolingModelAction(Class<?> toolingModel, List<String> tasks) {
        this.toolingModel = toolingModel;
        this.tasks = tasks;
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public String getShortDisplayName() {
        return "model " + toolingModel.getSimpleName();
    }

    @Override
    public String getDisplayName() {
        return "load tooling model " + toolingModel.getSimpleName();
    }

    @Override
    public void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs) {
        buildInvoker.loadToolingModel(tasks, gradleArgs, jvmArgs, toolingModel);
    }
}
