package org.gradle.trace.buildops;

import org.gradle.internal.operations.OperationFinishEvent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

final class TimeToLastInclusiveBuildOperationMeasurer implements BuildOperationMeasurer {
    private final long buildStartTime;
    private final AtomicLong maxEndTime;

    TimeToLastInclusiveBuildOperationMeasurer(long buildStartTime) {
        this.buildStartTime = buildStartTime;
        // Make sure we're never earlier than the build start time, even if no operations are recorded
        this.maxEndTime = new AtomicLong(buildStartTime);
    }

    @Override
    public void update(OperationFinishEvent event) {
        long endTime = event.getEndTime();
        maxEndTime.getAndUpdate(existing -> Math.max(existing, endTime));
    }

    @Override
    public Duration computeFinalValue() {
        return Duration.ofMillis(maxEndTime.get() - buildStartTime);
    }
}
