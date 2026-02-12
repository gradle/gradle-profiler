package org.gradle.trace.buildops;

import org.gradle.internal.operations.OperationFinishEvent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

final class TimeToFirstStartedBuildOperationMeasurer implements BuildOperationMeasurer {
    private final long buildStartTime;
    private final AtomicLong minStartTime = new AtomicLong(Long.MIN_VALUE);

    TimeToFirstStartedBuildOperationMeasurer(long buildStartTime) {
        this.buildStartTime = buildStartTime;
    }

    @Override
    public void update(OperationFinishEvent event) {
        long startTime = event.getStartTime();
        minStartTime.getAndUpdate(existing -> {
            if (existing == Long.MIN_VALUE) {
                // First update, initialize to the start time of the first operation
                return startTime;
            }
            return Math.min(existing, startTime);
        });
    }

    @Override
    public Duration computeFinalValue() {
        long minStart = minStartTime.get();
        if (minStart < buildStartTime) {
            // No operations were recorded, or all operations started before the build start time
            return Duration.ZERO;
        }
        return Duration.ofMillis(minStartTime.get() - buildStartTime);
    }
}
