package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest

class BuildOperationsTraceGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    def "can enable build operations trace via command-line flag"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "--build-ops-trace", "help"])

        then:
        logFile.containsOne("Build operations trace: ")
    }

    def "can enable build operations trace via scenario file"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                tasks = ["help"]
                build-ops-trace = true
            }
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--benchmark", "--build-ops-trace",
            "--scenario-file", scenarioFile.absolutePath,
            "s1"
        ])

        then:
        logFile.containsOne("Build operations trace: ")
    }

    def "build operations trace is disabled by default"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "help"])

        then:
        !logFile.find("Build operations trace: ").any()
    }

    def "command-line flag overrides scenario file setting"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                tasks = ["help"]
                build-ops-trace = false
            }
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--benchmark", "--build-ops-trace",
            "--scenario-file", scenarioFile.absolutePath,
            "s1"
        ])

        then:
        logFile.containsOne("Build operations trace: ")
    }
}
