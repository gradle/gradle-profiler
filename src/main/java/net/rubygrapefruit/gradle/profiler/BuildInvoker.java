package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.rubygrapefruit.gradle.profiler.Logging.*;

class BuildInvoker {
    private final ProjectConnection projectConnection;
    private final List<String> jvmArgs;
    private final PidInstrumentation pidInstrumentation;
    private final Consumer<BuildInvocationResult> resultsConsumer;

    public BuildInvoker(ProjectConnection projectConnection, List<String> jvmArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        this.projectConnection = projectConnection;
        this.jvmArgs = jvmArgs;
        this.pidInstrumentation = pidInstrumentation;
        this.resultsConsumer = resultsConsumer;
    }

    public BuildInvocationResult runBuild(String displayName, List<String> tasks) throws IOException {
        startOperation("Running " + displayName + " with tasks " + tasks);

        Timer timer = new Timer();
        run(projectConnection.newBuild(), build -> {
            build.forTasks(tasks.toArray(new String[0]));
            build.withArguments(pidInstrumentation.getArgs());
            build.setJvmArguments(jvmArgs);
            build.run();
            return null;
        });
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        System.out.println("Used daemon with pid " + pid);
        System.out.println("Execution time " + executionTime.toMillis() + "ms");

        BuildInvocationResult results = new BuildInvocationResult(displayName, executionTime, pid);
        resultsConsumer.accept(results);
        return results;
    }

    public static <T extends LongRunningOperation, R> R run(T operation, Function<T, R> function) {
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
}
