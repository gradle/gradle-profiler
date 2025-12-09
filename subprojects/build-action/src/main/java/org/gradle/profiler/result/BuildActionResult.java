package org.gradle.profiler.result;

import java.time.Duration;

public class BuildActionResult {

    private final Duration executionTime;

    public BuildActionResult(Duration executionTime) {
        this.executionTime = executionTime;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }
}
