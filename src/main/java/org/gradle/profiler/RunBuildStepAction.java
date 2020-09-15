package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.time.Duration;

import static org.gradle.profiler.Logging.startOperation;

public class RunBuildStepAction<T extends BuildInvocationResult> implements BuildStepAction<T> {
    private final BuildStepAction<? extends T> action;
    private final BuildMutator mutator;

    public RunBuildStepAction(BuildStepAction<? extends T> action, BuildMutator mutator) {
        this.action = action;
        this.mutator = mutator;
    }

    @Override
    public boolean isDoesSomething() {
        return action.isDoesSomething();
    }

    @Override
    public T run(BuildContext buildContext, BuildStep buildStep) {
        startOperation("Running " + buildContext.getDisplayName());
        mutator.beforeBuild(buildContext);
        RuntimeException failure = null;
        try {
            T result = action.run(buildContext, buildStep);
            printExecutionTime(result.getExecutionTime());
            return result;
        } catch (RuntimeException e) {
            failure = e;
        } catch (Throwable t) {
            failure = new RuntimeException(t);
        } finally {
            mutator.afterBuild(buildContext, failure);
        }
        throw failure;
    }

    private static void printExecutionTime(Duration executionTime) {
        System.out.println("Execution time " + executionTime.toMillis() + " ms");
    }
}
