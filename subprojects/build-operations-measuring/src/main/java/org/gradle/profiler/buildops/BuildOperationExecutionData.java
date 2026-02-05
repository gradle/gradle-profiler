package org.gradle.profiler.buildops;

/**
 * Carries information about the execution of a specific build operation type.
 * {@code value} is already computed according to the {@link BuildOperationMeasurementKind}.
 */
public class BuildOperationExecutionData {

    public static final BuildOperationExecutionData ZERO = new BuildOperationExecutionData(0, 0);

    private final long value;
    private final int totalCount;

    public BuildOperationExecutionData(long value, int totalCount) {
        this.value = value;
        this.totalCount = totalCount;
    }

    public long getValue() {
        return value;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
