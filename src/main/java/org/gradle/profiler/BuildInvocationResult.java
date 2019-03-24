package org.gradle.profiler;

import java.time.Duration;

public class BuildInvocationResult {
    private final String displayName;
    private final Duration executionTime;

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
