package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.LongRunningOperation;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.rubygrapefruit.gradle.profiler.Logging.*;

abstract class BuildInvoker {
    private final List<String> jvmArgs;
    private final PidInstrumentation pidInstrumentation;
    private final Consumer<BuildInvocationResult> resultsConsumer;

    public BuildInvoker(List<String> jvmArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        this.jvmArgs = jvmArgs;
        this.pidInstrumentation = pidInstrumentation;
        this.resultsConsumer = resultsConsumer;
    }

    public BuildInvocationResult runBuild(String displayName, List<String> tasks) {
        startOperation("Running " + displayName + " with tasks " + tasks);

        Timer timer = new Timer();
        run(tasks, pidInstrumentation.getArgs(), jvmArgs);
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        System.out.println("Used daemon with pid " + pid);
        System.out.println("Execution time " + executionTime.toMillis() + "ms");

        BuildInvocationResult results = new BuildInvocationResult(displayName, executionTime, pid);
        resultsConsumer.accept(results);
        return results;
    }

    protected abstract void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs);

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
