package org.gradle.profiler

import groovy.transform.SelfType

@SelfType(AbstractProfilerIntegrationTest)
trait FlameGraphFixture {

    void assertGraphsGenerated(String scenarioName = null, boolean multipleScenarios = false, String... events = ["cpu"]) {
        String scenarioPrefix = scenarioName ? "${scenarioName}-" : ""
        assert !(multipleScenarios && scenarioName == null)
        def outputBaseDir = multipleScenarios ? new File(outputDir, scenarioName) : outputDir
        events.each { event ->
            ["raw", "simplified"].each { type ->
                assert new File(outputBaseDir, "${scenarioPrefix}${latestSupportedGradleVersion}-${event}-${type}-flames.svg").file
                assert new File(outputBaseDir, "${scenarioPrefix}${latestSupportedGradleVersion}-${event}-${type}-icicles.svg").file
            }
        }
    }

    void assertGraphsGenerated(List<String> scenarios, String... events = ["cpu"]) {
        scenarios.each { scenario ->
            assertGraphsGenerated(scenario, true, events)
        }
    }

    void assertDifferentialGraphsGenerated(List<String> scenarios = [null], List<String> versions, String... events = ["cpu"]) {
        def multipleScenarios = scenarios.size() > 1
        def multipleVersions = versions.size() > 1
        assert !(multipleScenarios && multipleVersions)
        def variation = multipleScenarios ? scenarios : versions
        variation.each { current ->
            variation.findAll { it != current }.each {baseline ->
                ["backward", "forward"].each { diffType ->
                    events.each { event ->
                        ["simplified"].each { type ->
                            def scenarioPrefix = multipleScenarios ? "${current}-" : (scenarios.get(0) == null ? "" : "${scenario.get(0)}-")
                            def versionPostfix = multipleVersions ? ${current} : versions.get(0)
                            assert new File(outputDir, "${current}/diffs/${scenarioPrefix}${versionPostfix}-vs-${baseline}-${event}-${type}-${diffType}-diff-flames.svg").file
                            assert new File(outputDir, "${current}/diffs/${scenarioPrefix}${versionPostfix}-vs-${baseline}-${event}-${type}-${diffType}-diff-icicles.svg").file
                        }
                    }
                }
            }
        }
    }
}
