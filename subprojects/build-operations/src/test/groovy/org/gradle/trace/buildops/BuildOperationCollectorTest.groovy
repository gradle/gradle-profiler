package org.gradle.trace.buildops

import org.gradle.internal.operations.OperationFinishEvent
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.TIME_TO_FIRST_EXCLUSIVE

class BuildOperationCollectorTest extends Specification {

    @TempDir
    Path tempDir

    def "write overwrites the previous run's content when the measured value is present"() {
        given:
        def outputFile = tempDir.resolve("output.txt")
        Files.write(outputFile, ["12345,1"])
        def collector = new BuildOperationTrace.BuildOperationCollector(Object, outputFile, BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE))
        collector.collect(new Object(), new OperationFinishEvent(250, 400, null, null))

        when:
        collector.write(OptionalLong.of(100))

        then:
        Files.readAllLines(outputFile) == ["150,1"]
    }

    def "write truncates the previous run's content when the measured value is absent"() {
        given:
        def outputFile = tempDir.resolve("output.txt")
        Files.write(outputFile, ["12345,1"])
        def collector = new BuildOperationTrace.BuildOperationCollector(Object, outputFile, BuildOperationMeasurer.createForKind(TIME_TO_FIRST_EXCLUSIVE))
        collector.collect(new Object(), new OperationFinishEvent(250, 400, null, null))

        when:
        collector.write(OptionalLong.empty())

        then:
        Files.size(outputFile) == 0
    }
}
