package org.gradle.trace.buildops

import org.gradle.profiler.buildops.BuildOperationMeasurementKind
import spock.lang.Specification

import java.time.Duration
import java.util.Optional
import java.util.OptionalLong

import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.CUMULATIVE_TIME
import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.TIME_TO_FIRST_EXCLUSIVE
import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.TIME_TO_LAST_INCLUSIVE
import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.WALL_CLOCK_TIME

class BuildOperationMeasurerTest extends Specification {

    def "can create measurer for valid measurement kind '#kind'"() {
        expect:
        BuildOperationMeasurer.createForKind(kind) != null

        where:
        kind << BuildOperationMeasurementKind.values()
    }

    // --- CUMULATIVE_TIME ---

    def "cumulative time is measured even when build start time is absent"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME)

        when:
        measurer.update(100, 300)

        then:
        measurer.computeFinalValue(OptionalLong.empty()) == Optional.of(Duration.ofMillis(200))
    }

    def "cumulative time with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME)

        expect:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ZERO)
    }

    def "cumulative time with single event returns its duration"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME)

        when:
        measurer.update(100, 300)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(200))
    }

    def "cumulative time sums all event durations"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME)

        when:
        measurer.update(100, 200)
        measurer.update(300, 500)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(300))
    }

    def "cumulative time counts overlapping durations multiple times"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME)

        when:
        measurer.update(100, 300)
        measurer.update(200, 400)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(400))
    }

    def "cumulative time sums correctly when events arrive out of order"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME)

        when:
        measurer.update(300, 500)
        measurer.update(100, 200)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(300))
    }

    // --- WALL_CLOCK_TIME ---

    def "wall clock time is measured even when build start time is absent"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME)

        when:
        measurer.update(100, 300)

        then:
        measurer.computeFinalValue(OptionalLong.empty()) == Optional.of(Duration.ofMillis(200))
    }

    def "wall clock time with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME)

        expect:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ZERO)
    }

    def "wall clock time with single event returns its duration"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME)

        when:
        measurer.update(100, 300)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(200))
    }

    def "wall clock time sums non-overlapping event durations"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME)

        when:
        measurer.update(100, 200)
        measurer.update(300, 500)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(300))
    }

    def "wall clock time merges overlapping events"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME)

        when:
        measurer.update(100, 300)
        measurer.update(200, 400)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(300))
    }

    def "wall clock time merges overlapping events arriving out of order"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME)

        when:
        measurer.update(100, 300)
        measurer.update(400, 600)
        measurer.update(250, 350)

        then:
        measurer.computeFinalValue(OptionalLong.of(0L)) == Optional.of(Duration.ofMillis(450))
    }

    // --- TIME_TO_FIRST_EXCLUSIVE ---

    def "time to first exclusive with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE)

        expect:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ZERO)
    }

    def "time to first exclusive with single event returns time from build start to event start"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE)

        when:
        measurer.update(250, 400)

        then:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ofMillis(150))
    }

    def "time to first exclusive returns time to earliest event start"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE)

        when:
        measurer.update(400, 500)
        measurer.update(200, 300)
        measurer.update(300, 450)

        then:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ofMillis(100))
    }

    def "time to first exclusive returns empty when build start time is absent"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE)

        when:
        measurer.update(250, 400)

        then:
        measurer.computeFinalValue(OptionalLong.empty()) == Optional.empty()
    }

    def "time to first exclusive returns zero when all events started before the build start time"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE)

        when:
        measurer.update(50, 300)

        then:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ZERO)
    }

    // --- TIME_TO_LAST_INCLUSIVE ---

    def "time to last inclusive with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE)

        expect:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ZERO)
    }

    def "time to last inclusive with single event returns time from build start to event end"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE)

        when:
        measurer.update(200, 500)

        then:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ofMillis(400))
    }

    def "time to last inclusive returns time to latest event end"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE)

        when:
        measurer.update(200, 300)
        measurer.update(250, 600)
        measurer.update(400, 500)

        then:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ofMillis(500))
    }

    def "time to last inclusive returns empty when build start time is absent"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE)

        when:
        measurer.update(200, 500)

        then:
        measurer.computeFinalValue(OptionalLong.empty()) == Optional.empty()
    }

    def "time to last inclusive returns zero when all events ended before the build start time"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE)

        when:
        measurer.update(20, 50)

        then:
        measurer.computeFinalValue(OptionalLong.of(100L)) == Optional.of(Duration.ZERO)
    }
}
