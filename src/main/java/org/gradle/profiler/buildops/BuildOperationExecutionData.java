package org.gradle.profiler.buildops;

/**
 * Carries total duration and count of one or more invocations of a build operation.
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
