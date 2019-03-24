package org.gradle.profiler

class GradleInvocationIntegrationTest extends AbstractProfilerIntegrationTest {
    def "can benchmark using tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "assemble")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.containsOne("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 6
        logFile.find("* Running measured build").size() == 10
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.find("<daemon: true").size() == 17
        logFile.find("<tasks: [help]>").size() == 1
        logFile.find("<tasks: [assemble]>").size() == 16
        logFile.containsOne("<invocations: 16>")

        def lines = resultFile.lines
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
        logFile.containsOne("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 6
        logFile.find("* Running measured build").size() == 10
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.find("<daemon: true").size() == 17
        logFile.find("<tasks: [help]>").size() == 1
        logFile.find("<tasks: [assemble]>").size() == 16
        logFile.containsOne("<invocations: 16>")

        def lines = resultFile.lines
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
        logFile.containsOne("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == 10
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 12
        logFile.find("<daemon: true").size() == 12
        logFile.find("<tasks: [help]>").size() == 1
        logFile.find("<tasks: [assemble]>").size() == 11
        logFile.find("<invocations: 1>").size() == 12

        def lines = resultFile.lines
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
        logFile.containsOne("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == 10
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 12
        logFile.find("<daemon: true").size() == 12
        logFile.find("<tasks: [help]>").size() == 1
        logFile.find("<tasks: [assemble]>").size() == 11
        logFile.find("<invocations: 1>").size() == 12

        def lines = resultFile.lines
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
        logFile.containsOne("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == 10
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 12
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 11
        logFile.find("<tasks: [help]>").size() == 1
        logFile.find("<tasks: [assemble]>").size() == 11
        logFile.find("<invocations: 1>").size() == 12

        def lines = resultFile.lines
        lines.size() == 21 // 3 headers, 11 executions, 7 stats
        lines.get(0) == "scenario,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble"
    }
}
