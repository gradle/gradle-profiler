package org.gradle.profiler.gradle;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Performs operations on a build using Gradle.
 */
public interface GradleInvoker {
    void runTasks(List<String> tasks, List<String> gradleArgs, @Nullable File javaHome, List<String> jvmArgs);

    void loadToolingModel(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel);

    <T> T runToolingAction(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, BuildAction<T> action, Consumer<BuildActionExecuter<?>> configureAction);
}
