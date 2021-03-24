package org.gradle.profiler

import groovy.transform.SelfType

@SelfType(AbstractProfilerIntegrationTest)
trait FlameGraphFixture {

    void assertGraphsGeneratedForScenarios(String... scenarios) {
        assertGraphsGenerated(scenarios as List<String>, [latestSupportedGradleVersion], ['cpu'])
    }

    void assertGraphsGeneratedForScenario(String... events = ["cpu"]) {
        assertGraphsGenerated([null], [latestSupportedGradleVersion], events as List<String>)
    }

    void assertGraphsGeneratedForVersions(String... versions) {
        assertGraphsGenerated([null], versions as List<String>, ['cpu'])
    }

    void assertGraphsGenerated(List<String> scenarios, List<String> versions, List<String> events) {
        assert !scenarios.empty
        assert !versions.empty
        boolean multipleScenarios = scenarios.size() > 1
        boolean multipleVersions = versions.size() > 1
        scenarios.each { scenarioName ->
            String scenarioPrefix = scenarioName ? "${scenarioName}-" : ""
            versions.each { version ->
                def outputBaseDir = multipleScenarios ? new File(outputDir, scenarioName) : outputDir
                outputBaseDir = multipleVersions ? new File(outputBaseDir, version) : outputBaseDir
                events.each { event ->
                    ["raw", "simplified"].each { type ->
                        assert new File(outputBaseDir, "${scenarioPrefix}${version}-${event}-${type}-flames.svg").file
                        assert new File(outputBaseDir, "${scenarioPrefix}${version}-${event}-${type}-icicles.svg").file
                    }
                }
            }
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
                            def scenarioPrefix = multipleScenarios ? "${current}-" : (scenarios.get(0) == null ? "" : "${scenarios.get(0)}-")
                            def versionPostfix = multipleVersions ? current : versions.get(0)
                            assert new File(outputDir, "${current}/diffs/${scenarioPrefix}${versionPostfix}-vs-${baseline}-${event}-${type}-${diffType}-diff-flames.svg").file
                            assert new File(outputDir, "${current}/diffs/${scenarioPrefix}${versionPostfix}-vs-${baseline}-${event}-${type}-${diffType}-diff-icicles.svg").file
                        }
                    }
                }
            }
        }
    }

    void assertNoDifferentialFlameGraphsGenerated() {
        outputDir.listFiles()?.each { scenario ->
            assert !(new File(scenario, "diffs").exists())
        }
    }
}
