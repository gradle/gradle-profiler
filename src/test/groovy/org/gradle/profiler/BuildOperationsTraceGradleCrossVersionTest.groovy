package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest

class BuildOperationsTraceGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    def "can enable build operations trace via command-line flag"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "--build-ops-trace", "help"])

        then:
        logFile.containsOne("Build operations trace: true")
        logFile.containsOne("* Producing Perfetto trace from build operations of the last iteration")

        and:
        outputFile("default-log.txt").exists()
        outputFile("default.perfetto.proto").exists()
    }

    def "can enable build operations trace via scenario file"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            scenario1 {
                tasks = ["help"]
                build-ops-trace = true
            }
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--benchmark", "--build-ops-trace",
            "--scenario-file", scenarioFile.absolutePath,
            "scenario1"
        ])

        then:
        logFile.containsOne("Build operations trace: true")
        logFile.containsOne("* Producing Perfetto trace from build operations of the last iteration")

        and:
        outputFile("scenario1-log.txt").exists()
        outputFile("scenario1.perfetto.proto").exists()
    }

    def "build operations trace is disabled by default"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "help"])

        then:
        logFile.find("Build operations trace: false")

        and:
        outputDir.listFiles()
            .findAll { it.name.endsWith("-log.txt") || it.name.endsWith(".perfetto.proto") } == []
    }

    def "command-line flag overrides scenario file setting"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            scenario1 {
                tasks = ["help"]
                build-ops-trace = false
            }
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--benchmark", "--build-ops-trace",
            "--scenario-file", scenarioFile.absolutePath,
            "scenario1"
        ])

        then:
        logFile.containsOne("Build operations trace: true")
        logFile.containsOne("* Producing Perfetto trace from build operations of the last iteration")

        and:
        outputFile("scenario1-log.txt").exists()
        outputFile("scenario1.perfetto.proto").exists()
    }

    def "produces build operations traces in scenario subdirectories for grouped scenarios"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            scenario-groups {
                my-group = ["scenario1", "scenario2"]
            }
            scenario1 {
                tasks = ["help"]
                build-ops-trace = true
            }
            scenario2 {
                tasks = ["help"]
                build-ops-trace = true
            }
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.absolutePath,
            "--group", "my-group"
        ])

        then:
        outputFile("scenario1/scenario1-log.txt").exists()
        outputFile("scenario1/scenario1.perfetto.proto").exists()

        and:
        outputFile("scenario2/scenario2-log.txt").exists()
        outputFile("scenario2/scenario2.perfetto.proto").exists()
    }
}
