package org.gradle.profiler;

import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.util.List;

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

    public List<Duration> getMetrics() {
        return ImmutableList.of(executionTime);
    }
}
