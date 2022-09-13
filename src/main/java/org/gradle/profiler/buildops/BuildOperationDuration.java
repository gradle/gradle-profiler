package org.gradle.profiler.buildops;

import java.time.Duration;

/**
 * Carries total duration and count of one or more invocations of a build operation.
 */
public class BuildOperationDuration {

    private long totalDurationMillis;
    private int totalCount;

    public Duration getTotalDuration() {
        return Duration.ofMillis(totalDurationMillis);
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void add(long durationMillis, int count) {
        totalDurationMillis += durationMillis;
        totalCount += count;
    }
}
