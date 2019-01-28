package org.gradle.profiler;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class ScenarioInvoker<T extends ScenarioDefinition> {
    abstract void run(T scenario, InvocationSettings settings, BenchmarkResultCollector benchmarkResults) throws IOException, InterruptedException;

    <T> T tryRun(Supplier<T> action, Consumer<Throwable> after) {
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

    void tryRun(Runnable action, Consumer<Throwable> after) {
        tryRun(() -> {
            action.run();
            return null;
        }, after);
    }
}
