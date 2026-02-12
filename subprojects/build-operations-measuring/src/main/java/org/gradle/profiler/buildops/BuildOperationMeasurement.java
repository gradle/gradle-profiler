package org.gradle.profiler.buildops;

import java.util.Objects;

/**
 * Represents an operation and how it should be measured during a Gradle build.
 */
public final class BuildOperationMeasurement  {
    private final String buildOperationType;
    private final BuildOperationMeasurementKind measurementKind;

    public BuildOperationMeasurement(String buildOperationType, BuildOperationMeasurementKind measurementKind) {
        this.buildOperationType = buildOperationType;
        this.measurementKind = measurementKind;
    }

    /**
     * The type of build operation to be measured.
     */
    public String getBuildOperationType() {
        return buildOperationType;
    }

    /**
     * The kind of measurement to be performed on the build operation.
     */
    public BuildOperationMeasurementKind getMeasurementKind() {
        return measurementKind;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BuildOperationMeasurement)) {
            return false;
        }

        BuildOperationMeasurement that = (BuildOperationMeasurement) o;
        return Objects.equals(buildOperationType, that.buildOperationType) && measurementKind == that.measurementKind;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(buildOperationType);
        result = 31 * result + Objects.hashCode(measurementKind);
        return result;
    }

    public String toDisplayString() {
        return BuildOperationUtil.getSimpleBuildOperationName(buildOperationType)
            + " (" + measurementKind.toDisplayString() + ")";
    }

    @Override
    public String toString() {
        return "BuildOperationMeasurement{" +
            "buildOperationType='" + buildOperationType + "'" +
            ",measurementKind=" + measurementKind +
            '}';
    }
}
