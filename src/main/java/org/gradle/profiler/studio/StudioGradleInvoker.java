package org.gradle.profiler.studio;

import org.gradle.profiler.GradleInvoker;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;

import java.util.List;
import java.util.function.Consumer;

public class StudioGradleInvoker extends GradleInvoker {
    @Override
    public void runTasks(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void loadToolingModel(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T runToolingAction(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, BuildAction<T> action, Consumer<BuildActionExecuter<?>> configureAction) {
        throw new UnsupportedOperationException();
    }
}
