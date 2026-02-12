package org.gradle.trace.buildops

import org.gradle.profiler.buildops.BuildOperationMeasurementKind
import spock.lang.Specification

class BuildOperationMeasurerTest extends Specification {
    def "can create measurer for valid measurement kind '#kind'"() {
        expect:
        BuildOperationMeasurer.createForKind(kind, 0L) != null

        where:
        kind << BuildOperationMeasurementKind.values()
    }
}
