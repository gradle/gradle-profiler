package org.gradle.profiler.buildops;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How build operations should be measured in the results. This determines how to go from the potentially
 * multiple build operations collected during the build to the single value reported in the results.
 */
public enum BuildOperationMeasurementKind {
    /**
     * Sums the duration of all build operations of the given type.
     * Useful for measuring total time spent in a given type of operation.
     * If operations run in parallel, this will count the overlapping part multiple times.
     */
    CUMULATIVE_TIME,
    /**
     * Measures the time from the start of the build to the start of the first started build operation of the given type.
     * Note that this is based on the start timestamp, not any particular ordering given to the operation listener.
     */
    TIME_TO_FIRST_EXCLUSIVE,
    /**
     * Measures the time from the start of the build to the end of the last completed build operation of the given type.
     * Note that this is based on the end timestamp, not any particular ordering given to the operation listener.
     */
    TIME_TO_LAST_INCLUSIVE,
    // If adding to this enum, also update the README.md documentation.
    ;

    /**
     * Convert a string value to the corresponding BuildOperationMeasurementKind.
     * The string values are case-insensitive and must be contained in the set returned by {@link #getValidValues()}.
     *
     * @param value the string value to convert
     * @return the corresponding BuildOperationMeasurementKind
     * @throws IllegalArgumentException if the value is not a valid {@link BuildOperationMeasurementKind}
     */
    public static BuildOperationMeasurementKind fromString(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "cumulative_time":
                return CUMULATIVE_TIME;
            case "time_to_last_inclusive":
                return TIME_TO_LAST_INCLUSIVE;
            case "time_to_first_exclusive":
                return TIME_TO_FIRST_EXCLUSIVE;
            default:
                // This error is user-facing, so make sure to include helpful information.
                throw new IllegalArgumentException("Invalid measurement kind '" + value + "'. Valid values are: " + getValidValues());
        }
    }

    /**
     * Returns the set of valid string values that can be used to specify a BuildOperationMeasurementKind.
     * These are case-insensitive.
     *
     * <p>
     * This set is guaranteed to be sorted lexicographically and to contain the lowercase string representations of all
     * enum values.
     * </p>
     */
    public static Set<String> getValidValues() {
        return Stream.of(values())
            .map(v -> v.name().toLowerCase(Locale.ROOT))
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public String toString() {
        switch (this) {
            case CUMULATIVE_TIME:
                return "Duration Sum";
            case TIME_TO_LAST_INCLUSIVE:
                return "Time to Last Completed";
            case TIME_TO_FIRST_EXCLUSIVE:
                return "Time to First Started";
            default:
                throw new IllegalStateException("Unexpected value: " + this);
        }
    }
}
