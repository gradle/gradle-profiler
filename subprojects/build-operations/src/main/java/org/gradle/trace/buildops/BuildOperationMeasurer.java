package org.gradle.trace.buildops;

import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.profiler.buildops.BuildOperationMeasurementKind;

import java.time.Duration;

/**
 * Implements the correct calculation based on what {@link BuildOperationMeasurementKind} is requested.
 * Must be thread-safe.
 */
interface BuildOperationMeasurer {
    static BuildOperationMeasurer createForKind(BuildOperationMeasurementKind kind, long buildStartTime) {
        switch (kind) {
            case CUMULATIVE_TIME:
                return new CumulativeTimeBuildOperationMeasurer();
            case TIME_TO_LAST_INCLUSIVE:
                return new TimeToLastInclusiveBuildOperationMeasurer(buildStartTime);
            case TIME_TO_FIRST_EXCLUSIVE:
                return new TimeToFirstExclusiveBuildOperationMeasurer(buildStartTime);
            default:
                throw new IllegalArgumentException("Unsupported BuildOperationMeasurementKind: " + kind);
        }
    }

    /**
     * Update the measurer with data from the finished operation.
     *
     * @param event the finished operation event
     */
    void update(OperationFinishEvent event);

    /**
     * Compute the final measured value.
     *
     * @return the final measured value
     */
    Duration computeFinalValue();
}
