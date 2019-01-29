package org.gradle.profiler;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.gradle.profiler.Logging.startOperation;

public abstract class ScenarioInvoker<T extends ScenarioDefinition> {
    abstract void run(T scenario, InvocationSettings settings, BenchmarkResultCollector benchmarkResults) throws IOException, InterruptedException;

    /**
     * Runs a single measured build and collects the result.
     */
    BuildInvocationResult runMeasured(String displayName, BuildMutator mutator, Supplier<? extends BuildInvocationResult> action, Consumer<? super BuildInvocationResult> consumer) {
        startOperation("Running " + displayName);
        mutator.beforeBuild();
        BuildInvocationResult result = tryRun(() -> {
            BuildInvocationResult result1 = action.get();
            printExecutionTime(result1.getExecutionTime());
            return result1;
        }, mutator::afterBuild);
        consumer.accept(result);
        return result;
    }

    private static void printExecutionTime(Duration executionTime) {
        System.out.println("Execution time " + executionTime.toMillis() + " ms");
    }

    /**
     * Runs a single clean-up build.
     */
    void runCleanup(String displayName, BuildMutator mutator, Runnable action) {
        startOperation("Running cleanup for " + displayName);
        mutator.beforeCleanup();
        tryRun(() -> action.run(), mutator::afterCleanup);
    }

    /**
     * Returns a {@link Supplier} that returns the result of the given command.
     */
    Supplier<BuildInvocationResult> measureCommandLineExecution(String displayName, List<String> commandLine, File workingDir) {
        return () -> {
            Timer timer = new Timer();
            new CommandExec().inDir(workingDir).run(commandLine);
            Duration executionTime = timer.elapsed();
            return new BuildInvocationResult(displayName, executionTime, null);
        };
    }

    private <T> T tryRun(Supplier<T> action, Consumer<Throwable> after) {
        Throwable error = null;
        try {
            return action.get();
        } catch (RuntimeException | Error ex) {
            error = ex;
            throw ex;
        } catch (Throwable ex) {
            error = ex;
            throw new RuntimeException(ex);
        } finally {
            after.accept(error);
        }
    }

    private void tryRun(Runnable action, Consumer<Throwable> after) {
        tryRun(() -> {
            action.run();
            return null;
        }, after);
    }
}
