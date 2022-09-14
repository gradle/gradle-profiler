package org.gradle.profiler.buildops;

import java.time.Duration;

/**
 * Carries total duration and count of one or more invocations of a build operation.
 */
public class BuildOperationExecutionData {

    public static final BuildOperationExecutionData ZERO = new BuildOperationExecutionData(Duration.ZERO, 0);

    private final Duration totalDuration;
    private final int totalCount;

    public BuildOperationExecutionData(Duration totalDuration, int totalCount) {
        this.totalDuration = totalDuration;
        this.totalCount = totalCount;
    }

    public Duration getTotalDuration() {
        return totalDuration;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
