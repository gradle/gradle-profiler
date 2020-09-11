package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import static org.gradle.profiler.Logging.startOperation;

public class RunCleanupStepAction<T extends BuildInvocationResult> implements BuildStepAction<T> {
    private final BuildStepAction<T> cleanupAction;
    private final BuildMutator mutator;

    public RunCleanupStepAction(BuildStepAction<T> cleanupAction, BuildMutator mutator) {
        this.cleanupAction = cleanupAction;
        this.mutator = mutator;
    }

    @Override
    public boolean isDoesSomething() {
        return cleanupAction.isDoesSomething();
    }

    @Override
    public T run(BuildContext buildContext, BuildStep buildStep) {
        if (cleanupAction.isDoesSomething()) {
            startOperation("Running cleanup for " + buildContext.getDisplayName());
            mutator.beforeCleanup(buildContext);
            RuntimeException failure = null;
            try {
                cleanupAction.run(buildContext, buildStep);
                return null;
            } catch (RuntimeException e) {
                failure = e;
            } catch (Throwable t) {
                failure = new RuntimeException(t);
            } finally {
                mutator.afterCleanup(buildContext, failure);
            }
            throw failure;
        }

        return null;
    }
}
