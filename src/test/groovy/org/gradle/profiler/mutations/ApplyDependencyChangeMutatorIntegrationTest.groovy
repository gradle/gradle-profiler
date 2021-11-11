package org.gradle.profiler.mutations

import org.gradle.profiler.AbstractProfilerIntegrationTest
import org.gradle.profiler.Main

class ApplyDependencyChangeMutatorIntegrationTest extends AbstractProfilerIntegrationTest {

    def "successfully benchmarks gradle projects with dependency mutations with default values"() {
        given:
        instrumentedBuildScript()
        println buildFile
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                tasks = assemble
                apply-dependency-change-to {
                    files = ["build.gradle"]
                }
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "s1")

        then:
        resultFile.containsWarmDaemonScenario(latestSupportedGradleVersion, "s1", ["assemble"])
    }

    def "successfully benchmarks gradle projects with dependency mutations"() {
        given:
        instrumentedBuildScript()
        println buildFile
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                tasks = assemble
                apply-dependency-change-to {
                    files = ["build.gradle"]
                    projects-set-size = 2
                    applied-projects-size = 1
                }
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "s1")

        then:
        resultFile.containsWarmDaemonScenario(latestSupportedGradleVersion, "s1", ["assemble"])
    }

    def "fails benchmarks gradle projects if projects set size is less than applied projects size"() {
        given:
        instrumentedBuildScript()
        println buildFile
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                tasks = assemble
                apply-dependency-change-to {
                    files = ["build.gradle"]
                    projects-set-size = 2
                    applied-projects-size = 3
                }
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "s1")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Projects-set-size should be at least equal to applied-project-size."
    }

}
