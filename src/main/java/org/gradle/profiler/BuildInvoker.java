package org.gradle.profiler;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.LongRunningOperation;

import java.util.List;
import java.util.function.Function;

public abstract class BuildInvoker {
    public abstract void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel);

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
