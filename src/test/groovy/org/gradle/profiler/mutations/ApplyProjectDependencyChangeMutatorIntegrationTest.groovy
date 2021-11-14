package org.gradle.profiler.mutations

import org.gradle.profiler.AbstractProfilerIntegrationTest
import org.gradle.profiler.Main

class ApplyProjectDependencyChangeMutatorIntegrationTest extends AbstractProfilerIntegrationTest {

    def "successfully benchmarks gradle projects with dependency mutations with default values"() {
        given:
        instrumentedBuildScript()
        println buildFile
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                tasks = assemble
                apply-project-dependency-change-to {
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
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                    projects-set-size = 2
                    applied-projects-set-size = 1
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
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                    projects-set-size = 2
                    applied-projects-set-size = 3
                }
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "s1")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Value 'projects-set-size' should be at least equal to 'applied-projects-set-size'."
    }

    def "fails benchmarks gradle projects if projects set size or applied-projects-set-size are not greater than 0"() {
        given:
        instrumentedBuildScript()
        println buildFile
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                tasks = assemble
                apply-project-dependency-change-to {
                    files = ["build.gradle"]
                    projects-set-size = $projectsSetSize
                    applied-projects-set-size = $appliedProjectsSetSize
                }
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "s1")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Values 'projects-set-size' and 'applied-projects-set-size' should be greater than 0."

        where:
        projectsSetSize | appliedProjectsSetSize
        0               | 1
        1               | 0
    }

}
