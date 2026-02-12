package org.gradle.trace.buildops;

import org.gradle.internal.operations.OperationFinishEvent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

final class CumulativeTimeBuildOperationMeasurer implements BuildOperationMeasurer {
    private final AtomicLong buildOperationTime = new AtomicLong(0);

    CumulativeTimeBuildOperationMeasurer() {
    }

    @Override
    public void update(OperationFinishEvent event) {
        long duration = event.getEndTime() - event.getStartTime();
        buildOperationTime.addAndGet(duration);
    }

    @Override
    public Duration computeFinalValue() {
        return Duration.ofMillis(buildOperationTime.get());
    }
}
