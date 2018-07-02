package org.gradle.profiler

import org.gradle.profiler.report.AbstractGenerator
import spock.lang.Specification

class BenchmarkResultCollectorTest extends Specification {
    def generator = Mock(AbstractGenerator)
    def collector = new BenchmarkResultCollector(generator)

    def "collects results for a single scenario"() {
        def scenario = scenario()
        def result1 = result()
        def result2 = result()
        BenchmarkResult result

        when:
        def consumer = collector.version(scenario)
        consumer.accept(result1)
        consumer.accept(result2)
        collector.write()

        then:
        1 * generator.write(_) >> { BenchmarkResult r ->
            result = r
        }
        result.scenarios.size() == 1

        and:
        def scenarioResult = result.scenarios[0]
        scenarioResult.scenarioDefinition == scenario
        scenarioResult.results == [result1, result2]
        !scenarioResult.baseline.present
    }

    def "uses first version as the baseline when multiple present"() {
        def scenario1 = scenario("one", "4.8")
        def scenario2 = scenario("one", "4.9")
        def scenario3 = scenario("two", "4.8")
        def scenario4 = scenario("two", "4.9")
        BenchmarkResult result

        when:
        collector.version(scenario1)
        collector.version(scenario2)
        collector.version(scenario3)
        collector.version(scenario4)
        collector.write()

        then:
        1 * generator.write(_) >> { BenchmarkResult r ->
            result = r
        }

        and:
        result.scenarios.size() == 4
        !result.scenarios[0].baseline.present
        result.scenarios[1].baseline.get() == result.scenarios[0]
        !result.scenarios[2].baseline.present
        result.scenarios[3].baseline.get() == result.scenarios[2]
    }

    def "uses first scenario as the baseline when a single version used"() {
        def scenario1 = scenario("one", "4.7")
        def scenario2 = scenario("two", "4.7")
        def scenario3 = scenario("three", "4.7")
        BenchmarkResult result

        when:
        collector.version(scenario1)
        collector.version(scenario2)
        collector.version(scenario3)
        collector.write()

        then:
        1 * generator.write(_) >> { BenchmarkResult r ->
            result = r
        }

        and:
        result.scenarios.size() == 3
        !result.scenarios[0].baseline.present
        result.scenarios[1].baseline.get() == result.scenarios[0]
        result.scenarios[2].baseline.get() == result.scenarios[0]
    }

    def result() {
        return Stub(BuildInvocationResult)
    }

    def scenario(String name = "one", String version = "4.6") {
        def definition = Stub(ScenarioDefinition)
        definition.name >> name
        definition.buildToolDisplayName >> "Gradle $version"
        return definition
    }
}
