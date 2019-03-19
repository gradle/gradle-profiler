package org.gradle.profiler

import groovy.transform.NotYetImplemented


class GradleInvocationIntegrationTest extends AbstractProfilerIntegrationTest {
    def "can benchmark using tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "assemble")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 6
        logFile.grep("* Running measured build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.grep("<daemon: true").size() == 17
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 16
        logFile.contains("<invocations: 16>")

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble"
        lines.get(3).matches("warm-up build #1,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+")
        lines.get(9).matches("measured build #1,\\d+")
        lines.get(10).matches("measured build #2,\\d+")
        lines.get(18).matches("measured build #10,\\d+")
        lines.get(19).matches("mean,\\d+\\.\\d+")
        lines.get(22).matches("median,\\d+\\.\\d+")
        lines.get(25).matches("stddev,\\d+\\.\\d+")
    }

    def "can benchmark using `gradle` command and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "--cli", "assemble")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 6
        logFile.grep("* Running measured build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.grep("<daemon: true").size() == 17
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 16
        logFile.contains("<invocations: 16>")

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble"
        lines.get(3).matches("warm-up build #1,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+")
        lines.get(9).matches("measured build #1,\\d+")
        lines.get(10).matches("measured build #2,\\d+")
        lines.get(18).matches("measured build #10,\\d+")
        lines.get(19).matches("mean,\\d+\\.\\d+")
        lines.get(22).matches("median,\\d+\\.\\d+")
        lines.get(25).matches("stddev,\\d+\\.\\d+")
    }

    def "can benchmark using tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--cold-daemon", "assemble")

        then:
        // Probe version, 1 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 1
        logFile.grep("* Running measured build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 12
        logFile.grep("<daemon: true").size() == 12
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 11
        logFile.grep("<invocations: 1>").size() == 12

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 21 // 3 headers, 11 executions, 7 stats
        lines.get(0) == "scenario,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble"
        lines.get(3).matches("warm-up build #1,\\d+")
        lines.get(4).matches("measured build #1,\\d+")
        lines.get(5).matches("measured build #2,\\d+")
        lines.get(13).matches("measured build #10,\\d+")
        lines.get(14).matches("mean,\\d+\\.\\d+")
        lines.get(17).matches("median,\\d+\\.\\d+")
        lines.get(20).matches("stddev,\\d+\\.\\d+")
    }

    def "can benchmark using `gradle` command and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--cold-daemon", "--cli", "assemble")

        then:
        // Probe version, 1 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 1
        logFile.grep("* Running measured build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 12
        logFile.grep("<daemon: true").size() == 12
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 11
        logFile.grep("<invocations: 1>").size() == 12

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 21 // 3 headers, 11 executions, 7 stats
        lines.get(0) == "scenario,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble"
        lines.get(3).matches("warm-up build #1,\\d+")
        lines.get(4).matches("measured build #1,\\d+")
        lines.get(5).matches("measured build #2,\\d+")
        lines.get(13).matches("measured build #10,\\d+")
        lines.get(14).matches("mean,\\d+\\.\\d+")
        lines.get(17).matches("median,\\d+\\.\\d+")
        lines.get(20).matches("stddev,\\d+\\.\\d+")
    }

    def "can benchmark using `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--no-daemon", "assemble")

        then:
        // Probe version, 1 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 1
        logFile.grep("* Running measured build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 12
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 11
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 11
        logFile.grep("<invocations: 1>").size() == 12

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 21 // 3 headers, 11 executions, 7 stats
        lines.get(0) == "scenario,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble"
    }

    @NotYetImplemented
    def "scenario file can define scenario that uses cold daemon and tooling API"() {
        expect: false
    }

    @NotYetImplemented
    def "scenario file can define scenario that uses cold daemon and `gradle` command"() {
        expect: false
    }

    @NotYetImplemented
    def "fails when benchmarking with no daemon and tooling API"() {
        expect: false
    }

    @NotYetImplemented
    def "fails when profiling with no daemon and tooling API"() {
        expect: false
    }
}
