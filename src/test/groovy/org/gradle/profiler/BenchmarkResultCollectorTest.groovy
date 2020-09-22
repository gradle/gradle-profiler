package org.gradle.profiler

import org.gradle.profiler.report.AbstractGenerator
import org.gradle.profiler.report.BenchmarkResult
import org.gradle.profiler.result.BuildInvocationResult
import org.gradle.profiler.result.Sample
import spock.lang.Specification

class BenchmarkResultCollectorTest extends Specification {
    def generator = Mock(AbstractGenerator)
    def collector = new BenchmarkResultCollector(generator)

    def "collects results for a single scenario"() {
        def scenario = scenario()
        def result1 = result()
        def result2 = result()
        BenchmarkResult result = null
        def settings = new InvocationSettings.InvocationSettingsBuilder().build()

        when:
        def consumer = collector.scenario(scenario, [sample()])
        consumer.accept(result1)
        consumer.accept(result2)
        collector.write(settings)

        then:
        1 * generator.write(settings, _) >> { InvocationSettings s, BenchmarkResult r ->
            result = r
        }
        result.scenarios.size() == 1

        and:
        def scenarioResult = result.scenarios[0]
        scenarioResult.scenarioDefinition == scenario
        scenarioResult.results == [result1, result2]
    }

    def sample() {
        return Stub(Sample)
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
