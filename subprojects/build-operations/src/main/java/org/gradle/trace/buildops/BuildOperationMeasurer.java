package org.gradle.trace.buildops;

import org.gradle.profiler.buildops.BuildOperationMeasurementKind;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Implements the correct calculation based on what {@link BuildOperationMeasurementKind} is requested.
 * Must be thread-safe.
 */
interface BuildOperationMeasurer {
    static BuildOperationMeasurer createForKind(BuildOperationMeasurementKind kind) {
        switch (kind) {
            case CUMULATIVE_TIME:
                return new CumulativeTimeBuildOperationMeasurer();
            case WALL_CLOCK_TIME:
                return new WallClockTimeBuildOperationMeasurer();
            case TIME_TO_LAST_INCLUSIVE:
                return new TimeToLastInclusiveBuildOperationMeasurer();
            case TIME_TO_FIRST_EXCLUSIVE:
                return new TimeToFirstExclusiveBuildOperationMeasurer();
            default:
                throw new IllegalArgumentException("Unsupported BuildOperationMeasurementKind: " + kind);
        }
    }

    /**
     * Update the measurer with data from a finished operation.
     *
     * @param startTime the absolute timestamp at which the operation started
     * @param endTime the absolute timestamp at which the operation ended
     */
    void update(long startTime, long endTime);

    /**
     * Compute the final measured value.
     *
     * @param buildStartTime the absolute timestamp at which the current build invocation started,
     * used as the reference point for measurement kinds that report a time within the build.
     * May be absent if it could not be observed, e.g. on configuration failure.
     * @return the final measured value, or empty if the build start time is required but absent
     */
    Optional<Duration> computeFinalValue(OptionalLong buildStartTime);
}
