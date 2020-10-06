package org.gradle.profiler;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ToolingApiGradleClient implements GradleInvoker, GradleClient {
    private final ProjectConnection projectConnection;

    public ToolingApiGradleClient(ProjectConnection projectConnection) {
        this.projectConnection = projectConnection;
    }

    @Override
    public void close() {
        projectConnection.close();
    }

    private static <T extends LongRunningOperation, R> R run(T operation, Function<T, R> function) {
        operation.setStandardOutput(Logging.detailed());
        operation.setStandardError(Logging.detailed());
        try {
            return function.apply(operation);
        } catch (GradleConnectionException e) {
            System.out.println();
            System.out.println("ERROR: failed to run build. See log file for details.");
            System.out.println();
            throw e;
        }
    }

    public <T extends LongRunningOperation, R> R runOperation(
        Function<ProjectConnection, T> createOperation,
        Function<T, R> operationAction
    ) {
        return run(createOperation.apply(projectConnection), operationAction);
    }

    @Override
    public void runTasks(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        runOperation(ProjectConnection::newBuild, build -> {
            build.forTasks(tasks.toArray(new String[0]));
            build.withArguments(gradleArgs);
            build.setJvmArguments(jvmArgs);
            build.run();
            return null;
        });
    }

    @Override
    public void loadToolingModel(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel) {
        runOperation(connection -> connection.model(toolingModel), build -> {
            build.forTasks(tasks.toArray(new String[0]));
            build.withArguments(gradleArgs);
            build.setJvmArguments(jvmArgs);
            build.get();
            return null;
        });
    }

    @Override
    public <T> T runToolingAction(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, BuildAction<T> action, Consumer<BuildActionExecuter<?>> configureAction) {
        return runOperation(connection -> connection.action(action), build -> {
            build.forTasks(tasks.toArray(new String[0]));
            build.withArguments(gradleArgs);
            build.setJvmArguments(jvmArgs);
            configureAction.accept(build);
            return build.run();
        });
    }
}

