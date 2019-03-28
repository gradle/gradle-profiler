package org.gradle.profiler.result;

import java.time.Duration;

public class BuildInvocationResult {
    private final String displayName;
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

    public BuildInvocationResult(String displayName, Duration executionTime) {
        this.displayName = displayName;
        this.executionTime = executionTime;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }
}
