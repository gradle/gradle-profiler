package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.BuildAction.BuildActionResult;

import java.time.Duration;

public class StudioBuildActionResult extends BuildActionResult {

    private final Duration gradleExecutionTime;
    private final Duration ideExecutionTime;

    public StudioBuildActionResult(Duration executionTime, Duration gradleExecutionTime, Duration ideExecutionTime) {
        super(executionTime);
        this.gradleExecutionTime = gradleExecutionTime;
        this.ideExecutionTime = ideExecutionTime;
    }

    public Duration getGradleExecutionTime() {
        return gradleExecutionTime;
    }

    public Duration getIdeExecutionTime() {
        return ideExecutionTime;
    }
}
