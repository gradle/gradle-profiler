package org.gradle.profiler;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;

import java.util.List;
import java.util.function.Consumer;

public abstract class BuildInvoker {
    public abstract void runTasks(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs);

    public abstract void loadToolingModel(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel);

    public abstract <T> T runToolingAction(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, BuildAction<T> action, Consumer<BuildActionExecuter<?>> configureAction);
}
