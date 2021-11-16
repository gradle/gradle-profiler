package org.gradle.profiler.mutations

import org.gradle.profiler.AbstractProfilerIntegrationTest
import org.gradle.profiler.Main

class ApplyProjectDependencyChangeMutatorIntegrationTest extends AbstractProfilerIntegrationTest {

    def scenarioName = "scenario"

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
                    projects-set-size = 2
                    applied-projects-set-size = 1
                }
            }
        """

        when:
        runBenchmark(scenarioFile)

        then:
        resultFile.containsWarmDaemonScenario(latestSupportedGradleVersion, scenarioName, ["assemble"])
    }

    def "fails benchmarks gradle projects if projects set size is less than applied projects size"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            $scenarioName {
                tasks = assemble
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                    projects-set-size = 2
                    applied-projects-set-size = 3
                }
            }
        """

        when:
        runBenchmark(scenarioFile)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Value 'projects-set-size' should be at least equal to 'applied-projects-set-size'."
    }

    def "fails benchmarks gradle projects if projects set size or applied-projects-set-size are not greater than 0"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            scenario {
                tasks = assemble
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                    projects-set-size = $projectsSetSize
                    applied-projects-set-size = $appliedProjectsSetSize
                }
            }
        """

        when:
        runBenchmark(scenarioFile)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Values 'projects-set-size' and 'applied-projects-set-size' should be greater than 0."

        where:
        projectsSetSize | appliedProjectsSetSize
        0               | 1
        1               | 0
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
