package org.gradle.profiler.result;

import org.gradle.profiler.BuildContext;

import java.time.Duration;

public class BuildInvocationResult {
    private final BuildContext buildContext;
    private final Duration executionTime;

    public static final Sample<BuildInvocationResult> EXECUTION_TIME = new Sample<BuildInvocationResult>() {
        @Override
        public String getName() {
            return "execution";
        }

        @Override
        public Duration extractFrom(BuildInvocationResult result) {
            return result.getExecutionTime();
        }
    };

    public BuildInvocationResult(BuildContext buildContext, Duration executionTime) {
        this.buildContext = buildContext;
        this.executionTime = executionTime;
    }

    public String getDisplayName() {
        return buildContext.getDisplayName();
    }

    public BuildContext getBuildContext() {
        return buildContext;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }
}
