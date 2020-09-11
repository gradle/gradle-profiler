package org.gradle.profiler;

import static org.gradle.profiler.Logging.startOperation;

public class CleanupStepAction implements BuildStepAction {
    private final BuildStepAction cleanupAction;
    private final BuildMutator mutator;

    public CleanupStepAction(BuildStepAction cleanupAction, BuildMutator mutator) {
        this.cleanupAction = cleanupAction;
        this.mutator = mutator;
    }

    @Override
    public boolean isDoesSomething() {
        return cleanupAction.isDoesSomething();
    }

    @Override
    public GradleBuildInvocationResult run(BuildContext buildContext, BuildStep buildStep) {
        if (cleanupAction.isDoesSomething()) {
            startOperation("Running cleanup for " + buildContext.getDisplayName());
            mutator.beforeCleanup(buildContext);
            Throwable failure = null;
            try {
                cleanupAction.run(buildContext, buildStep);
            } catch (Throwable t) {
                failure = t;
            } finally {
                mutator.afterCleanup(buildContext, failure);
            }
        }

        return null;
    }
}
