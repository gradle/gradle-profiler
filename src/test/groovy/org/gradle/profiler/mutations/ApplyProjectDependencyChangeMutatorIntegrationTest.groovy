package org.gradle.profiler.mutations

import org.gradle.profiler.AbstractProfilerIntegrationTest
import org.gradle.profiler.Main

class ApplyProjectDependencyChangeMutatorIntegrationTest extends AbstractProfilerIntegrationTest {

    String scenarioName

    def setup() {
        scenarioName = "scenario"
    }

    def "successfully benchmarks gradle projects with dependency mutations with default values"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                tasks = assemble
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                }
            }
        """

        when:
        runBenchmark(scenarioFile)

        then:
        resultFile.containsWarmDaemonScenario(latestSupportedGradleVersion, scenarioName, ["assemble"])
    }

    def "successfully benchmarks gradle projects with dependency mutations"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                tasks = assemble
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                    applied-projects-count = 2
                }
            }
        """

        when:
        runBenchmark(scenarioFile)

        then:
        resultFile.containsWarmDaemonScenario(latestSupportedGradleVersion, scenarioName, ["assemble"])
    }

    def "fails benchmarks gradle projects if applied-projects-count is less or equal to 0"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            scenario {
                tasks = assemble
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                    applied-projects-count = 0
                }
            }
        """

        when:
        runBenchmark(scenarioFile)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Value 'applied-projects-count' should be greater than 0."
    }

    def runBenchmark(File scenarioFile) {
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--scenario-file", scenarioFile.absolutePath,
            "--benchmark", scenarioName
        )
    }
}
