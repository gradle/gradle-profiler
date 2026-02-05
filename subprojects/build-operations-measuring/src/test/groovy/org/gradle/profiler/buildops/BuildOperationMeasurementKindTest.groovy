package org.gradle.profiler.buildops

import spock.lang.Specification

import java.util.stream.Stream

class BuildOperationMeasurementKindTest extends Specification {
    def "can parse measurement kind name '#name' as '#kind'"() {
        expect:
        BuildOperationMeasurementKind.fromString(name) == kind

        where:
        [name, kind] << Stream.of(BuildOperationMeasurementKind.values())
            .flatMap {
                Stream.of(
                    [it.name(), it],
                    [it.name().toLowerCase(Locale.ROOT), it],
                    [it.name().capitalize(), it]
                )
            }
    }

    def "invalid measurement kind name '#name' throws exception"() {
        when:
        BuildOperationMeasurementKind.fromString(name)
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains(name)
        where:
        name << ["invalid", "duration", "DURATION", "time_to"]
    }

    def "each valid value parses to a unique measurement kind name"() {
        expect:
        assert BuildOperationMeasurementKind.getValidValues().size() == BuildOperationMeasurementKind.values().length
        def seen = new HashSet<BuildOperationMeasurementKind>()
        BuildOperationMeasurementKind.getValidValues().each {
            def kind = BuildOperationMeasurementKind.fromString(it)
            assert seen.add(kind) : "valid value '$it' should parse to a unique measurement kind"
        }
    }
}
