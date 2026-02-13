package org.gradle.trace.buildops

import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.profiler.buildops.BuildOperationMeasurementKind
import spock.lang.Specification

import java.time.Duration

import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.CUMULATIVE_TIME
import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.TIME_TO_FIRST_EXCLUSIVE
import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.TIME_TO_LAST_INCLUSIVE
import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.WALL_CLOCK_TIME

class BuildOperationMeasurerTest extends Specification {

    def "can create measurer for valid measurement kind '#kind'"() {
        expect:
        BuildOperationMeasurer.createForKind(kind, 0L) != null

        where:
        kind << BuildOperationMeasurementKind.values()
    }

    // --- CUMULATIVE_TIME ---

    def "cumulative time with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME, 0L)

        expect:
        measurer.computeFinalValue() == Duration.ZERO
    }

    def "cumulative time with single event returns its duration"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME, 0L)

        when:
        measurer.update(event(100, 300))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(200)
    }

    def "cumulative time sums all event durations"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME, 0L)

        when:
        measurer.update(event(100, 200))
        measurer.update(event(300, 500))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(300)
    }

    def "cumulative time counts overlapping durations multiple times"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME, 0L)

        when:
        measurer.update(event(100, 300))
        measurer.update(event(200, 400))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(400)
    }

    def "cumulative time sums correctly when events arrive out of order"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(CUMULATIVE_TIME, 0L)

        when:
        measurer.update(event(300, 500))
        measurer.update(event(100, 200))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(300)
    }

    // --- WALL_CLOCK_TIME ---

    def "wall clock time with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME, 0L)

        expect:
        measurer.computeFinalValue() == Duration.ZERO
    }

    def "wall clock time with single event returns its duration"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME, 0L)

        when:
        measurer.update(event(100, 300))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(200)
    }

    def "wall clock time sums non-overlapping event durations"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME, 0L)

        when:
        measurer.update(event(100, 200))
        measurer.update(event(300, 500))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(300)
    }

    def "wall clock time merges overlapping events"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME, 0L)

        when:
        measurer.update(event(100, 300))
        measurer.update(event(200, 400))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(300)
    }

    def "wall clock time merges overlapping events arriving out of order"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(WALL_CLOCK_TIME, 0L)

        when:
        measurer.update(event(100, 300))
        measurer.update(event(400, 600))
        measurer.update(event(250, 350))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(450)
    }

    // --- TIME_TO_FIRST_EXCLUSIVE ---

    def "time to first exclusive with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE, 100L)

        expect:
        measurer.computeFinalValue() == Duration.ZERO
    }

    def "time to first exclusive with single event returns time from build start to event start"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE, 100L)

        when:
        measurer.update(event(250, 400))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(150)
    }

    def "time to first exclusive returns time to earliest event start"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE, 100L)

        when:
        measurer.update(event(400, 500))
        measurer.update(event(200, 300))
        measurer.update(event(300, 450))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(100)
    }

    // --- TIME_TO_LAST_INCLUSIVE ---

    def "time to last inclusive with no events returns zero"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE, 100L)

        expect:
        measurer.computeFinalValue() == Duration.ZERO
    }

    def "time to last inclusive with single event returns time from build start to event end"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE, 100L)

        when:
        measurer.update(event(200, 500))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(400)
    }

    def "time to last inclusive returns time to latest event end"() {
        given:
        def measurer = BuildOperationMeasurer.createForKind(TIME_TO_LAST_INCLUSIVE, 100L)

        when:
        measurer.update(event(200, 300))
        measurer.update(event(250, 600))
        measurer.update(event(400, 500))

        then:
        measurer.computeFinalValue() == Duration.ofMillis(500)
    }

    private static OperationFinishEvent event(long startTime, long endTime) {
        return new OperationFinishEvent(startTime, endTime, null, null)
    }
}
