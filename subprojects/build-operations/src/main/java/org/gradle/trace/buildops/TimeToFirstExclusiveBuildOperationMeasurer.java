package org.gradle.trace.buildops;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

final class TimeToFirstExclusiveBuildOperationMeasurer implements BuildOperationMeasurer {
    private final AtomicLong minStartTime = new AtomicLong(Long.MIN_VALUE);

    @Override
    public void update(long startTime, long endTime) {
        minStartTime.getAndUpdate(existing -> {
            if (existing == Long.MIN_VALUE) {
                // First update, initialize to the start time of the first operation
                return startTime;
            }
            return Math.min(existing, startTime);
        });
    }

    @Override
    public Optional<Duration> computeFinalValue(OptionalLong buildStartTime) {
        if (!buildStartTime.isPresent()) {
            // No reference point to measure against
            return Optional.empty();
        }
        long minStart = minStartTime.get();
        if (minStart == Long.MIN_VALUE || minStart < buildStartTime.getAsLong()) {
            // No operations were recorded, or all operations started before the build start time
            return Optional.of(Duration.ZERO);
        }
        return Optional.of(Duration.ofMillis(minStart - buildStartTime.getAsLong()));
    }
}
