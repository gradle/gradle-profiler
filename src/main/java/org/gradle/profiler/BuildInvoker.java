package org.gradle.profiler;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.LongRunningOperation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gradle.profiler.Logging.*;

public abstract class BuildInvoker {
    private final List<String> jvmArgs;
    private final List<String> gradleArgs;
    private final PidInstrumentation pidInstrumentation;
    private final Consumer<BuildInvocationResult> resultsConsumer;
    private final List<GeneratedInitScript> initScripts;

    public BuildInvoker(List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        this.jvmArgs = jvmArgs;
        this.gradleArgs = gradleArgs;
        this.pidInstrumentation = pidInstrumentation;
        this.resultsConsumer = resultsConsumer;
        this.initScripts = new ArrayList<>();
    }

    public BuildInvocationResult runBuild(String displayName, List<String> tasks) {
        startOperation("Running " + displayName + " with tasks " + tasks);

        List<String> allGradleArgs = new ArrayList<>(gradleArgs.size() + initScripts.size());
        allGradleArgs.addAll(gradleArgs);
        for (GeneratedInitScript initScript : initScripts) {
            allGradleArgs.addAll(initScript.getArgs());
        }

        Timer timer = new Timer();
        run(tasks, allGradleArgs, jvmArgs);
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        Logging.detailed().println("Used daemon with pid " + pid);
        System.out.println("Execution time " + executionTime.toMillis() + "ms");

        BuildInvocationResult results = new BuildInvocationResult(displayName, executionTime, pid);
        resultsConsumer.accept(results);
        return results;
    }

    protected abstract void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs);

    public void addInitScript(GeneratedInitScript initScript) {
        initScripts.add(initScript);
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
