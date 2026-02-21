package org.gradle.profiler.ide.invoker;


import org.gradle.profiler.result.BuildActionResult;

import java.time.Duration;
import java.util.List;

public class IdeBuildActionResult extends BuildActionResult {

    private final Duration gradleTotalExecutionTime;
    private final List<Duration> gradleExecutionTimes;
    private final Duration ideExecutionTime;

    public IdeBuildActionResult(Duration executionTime, Duration gradleTotalExecutionTime, List<Duration> gradleExecutionTimes, Duration ideExecutionTime) {
        super(executionTime);
        this.gradleTotalExecutionTime = gradleTotalExecutionTime;
        this.gradleExecutionTimes = gradleExecutionTimes;
        this.ideExecutionTime = ideExecutionTime;
    }

    public Duration getGradleTotalExecutionTime() {
        return gradleTotalExecutionTime;
    }

    public List<Duration> getGradleExecutionTimes() {
        return gradleExecutionTimes;
    }

    public Duration getIdeExecutionTime() {
        return ideExecutionTime;
    }
}
