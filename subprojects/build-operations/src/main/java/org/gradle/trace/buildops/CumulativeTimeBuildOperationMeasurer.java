package org.gradle.trace.buildops;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

final class CumulativeTimeBuildOperationMeasurer implements BuildOperationMeasurer {
    private final AtomicLong buildOperationTime = new AtomicLong(0);

    CumulativeTimeBuildOperationMeasurer() {
    }

    @Override
    public void update(long startTime, long endTime) {
        buildOperationTime.addAndGet(endTime - startTime);
    }

    @Override
    public Optional<Duration> computeFinalValue(OptionalLong buildStartTime) {
        return Optional.of(Duration.ofMillis(buildOperationTime.get()));
    }
}
