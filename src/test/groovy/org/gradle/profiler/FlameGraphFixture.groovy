package org.gradle.profiler

import groovy.transform.SelfType
import org.gradle.profiler.fixtures.AbstractBaseProfilerIntegrationTest
import org.gradle.profiler.flamegraph.FlamegraphGeneratorTestFixture

import java.nio.file.Path

@SelfType(AbstractBaseProfilerIntegrationTest)
trait FlameGraphFixture {

    void assertGraphsGeneratedForScenarios(String gradleVersion, List<String> scenarios, List<String> events) {
        assertGraphsGenerated(scenarios, [gradleVersion], events)
    }

    void assertGraphsGeneratedForScenario(String gradleVersion, List<String> events, boolean strict = true) {
        assertGraphsGenerated([null], [gradleVersion], events, strict)
    }

    void assertGraphsGeneratedForVersions(List<String> versions, List<String> events) {
        assertGraphsGenerated([null], versions, events)
    }

    void assertGraphsGenerated(List<String> scenarios, List<String> versions, List<String> events, boolean strict = true) {
        assert !scenarios.empty
        assert !versions.empty
        boolean multipleScenarios = scenarios.size() > 1
        boolean multipleVersions = versions.size() > 1
        scenarios.each { scenarioName ->
            String scenarioPrefix = scenarioName ? "${scenarioName}-" : ""
            versions.each { version ->
                def outputBaseDir = multipleScenarios ? outputDir.file(scenarioName) : outputDir
                outputBaseDir = multipleVersions ? outputBaseDir.file(version) : outputBaseDir

                def flamegraphFile = outputBaseDir.file("${scenarioPrefix}${version}-flames.html")
                List<Path> expectedStacks = []
                events.each { event ->
                    ["raw", "simplified"].each { type ->
                        expectedStacks << outputBaseDir.file("${scenarioPrefix}${version}-${event}-${type}-stacks.txt").toPath()
                    }
                }

                assert flamegraphFile.exists()
                FlamegraphGeneratorTestFixture.assertHasStacksFiles(flamegraphFile.toPath(), expectedStacks, strict)
            }
        }
    }

    void assertDifferentialGraphsGenerated(List<String> scenarios = [null], List<String> versions, List<String> events) {
        def multipleScenarios = scenarios.size() > 1
        def multipleVersions = versions.size() > 1
        assert !(multipleScenarios && multipleVersions)
        def variation = multipleScenarios ? scenarios : versions
        variation.each { current ->
            variation.findAll { it != current }.each { baseline ->
                def diffsDir = outputDir.file("${current}/diffs")
                List<Path> expectedStacks = []
                ["backward", "forward"].each { diffType ->
                    events.each { event ->
                        def scenarioPrefix = multipleScenarios ? "${current}-" : (scenarios.get(0) == null ? "" : "${scenarios.get(0)}-")
                        def versionPostfix = multipleVersions ? current : versions.get(0)
                        expectedStacks << diffsDir.file("${scenarioPrefix}${versionPostfix}-vs-${baseline}-${event}-simplified-${diffType}-diff-stacks.txt").toPath()
                    }
                }

                def flamegraphFile = diffsDir.file("diffs-flames.html")
                assert flamegraphFile.exists()
                FlamegraphGeneratorTestFixture.assertHasStacksFiles(flamegraphFile.toPath(), expectedStacks)
            }
        }
    }

    void assertNoDifferentialFlameGraphsGenerated() {
        outputDir.listFiles()?.each { scenario ->
            assert !(new File(scenario, "diffs").exists())
        }
    }
}
