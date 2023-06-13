package org.gradle.profiler.result;

import org.gradle.profiler.BuildContext;

import java.time.Duration;

public class BuildInvocationResult {
    private final BuildContext buildContext;
    private final BuildActionResult actionResult;

    public BuildInvocationResult(BuildContext buildContext, BuildActionResult actionResult) {
        this.buildContext = buildContext;
        this.actionResult = actionResult;
    }

    public String getDisplayName() {
        return buildContext.getDisplayName();
    }

    public BuildContext getBuildContext() {
        return buildContext;
    }

    public BuildActionResult getActionResult() {
        return actionResult;
    }

    public Duration getExecutionTime() {
        return actionResult.getExecutionTime();
    }

    public static final Sample<BuildInvocationResult> EXECUTION_TIME
        = SingleInvocationDurationSample.from("total execution time", BuildInvocationResult::getExecutionTime);
}
