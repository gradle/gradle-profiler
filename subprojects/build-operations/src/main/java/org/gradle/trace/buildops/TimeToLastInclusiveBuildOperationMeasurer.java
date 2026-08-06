package org.gradle.trace.buildops;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

final class TimeToLastInclusiveBuildOperationMeasurer implements BuildOperationMeasurer {
    private final AtomicLong maxEndTime = new AtomicLong(Long.MIN_VALUE);

    @Override
    public void update(long startTime, long endTime) {
        maxEndTime.getAndUpdate(existing -> Math.max(existing, endTime));
    }

    @Override
    public Optional<Duration> computeFinalValue(OptionalLong buildStartTime) {
        if (!buildStartTime.isPresent()) {
            // No reference point to measure against
            return Optional.empty();
        }
        long maxEnd = maxEndTime.get();
        if (maxEnd == Long.MIN_VALUE || maxEnd < buildStartTime.getAsLong()) {
            // No operations were recorded, or all operations ended before the build start time
            return Optional.of(Duration.ZERO);
        }
        return Optional.of(Duration.ofMillis(maxEnd - buildStartTime.getAsLong()));
    }
}
