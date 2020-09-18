package org.gradle.profiler


class BenchmarkIntegrationTest extends AbstractProfilerIntegrationTest {
    def "recovers from measured build failure running benchmarks"() {
        given:
        brokenBuild(8)

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--gradle-version", "5.0", "--benchmark", "assemble")

        then:
        def e = thrown(Main.ScenarioFailedException)
        logFile.containsOne(e.cause.message)
        output.contains(e.cause.message)
        logFile.containsOne("java.lang.RuntimeException: broken!")
        output.contains("java.lang.RuntimeException: broken!")

        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 10
        logFile.find("<gradle-version: 5.0>").size() == 17
        logFile.find("<tasks: [help]>").size() == 2
        logFile.find("<tasks: [assemble]>").size() == 9 + 16

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,Gradle ${minimalSupportedGradleVersion},Gradle 5.0"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,execution,execution"
        lines.get(4).matches("warm-up build #1,\\d+,\\d+")
        lines.get(9).matches("warm-up build #6,\\d+,\\d+")
        lines.get(10).matches("measured build #1,\\d+,\\d+")
        lines.get(11).matches("measured build #2,\\d+,\\d+")
        lines.get(12).matches("measured build #3,,\\d+")
        lines.get(19).matches("measured build #10,,\\d+")
    }

    def "recovers from failure in warm-up build while running benchmarks"() {
        given:
        brokenBuild(3)

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--gradle-version", "5.0", "--benchmark", "assemble")

        then:
        def e = thrown(Main.ScenarioFailedException)
        logFile.containsOne(e.cause.message)
        output.contains(e.cause.message)
        logFile.containsOne("java.lang.RuntimeException: broken!")
        output.contains("java.lang.RuntimeException: broken!")

        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 5
        logFile.find("<gradle-version: 5.0>").size() == 17
        logFile.find("<tasks: [help]>").size() == 2
        logFile.find("<tasks: [assemble]>").size() == 4 + 16

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,Gradle ${minimalSupportedGradleVersion},Gradle 5.0"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,execution,execution"
        lines.get(4).matches("warm-up build #1,\\d+,\\d+")
        lines.get(5).matches("warm-up build #2,\\d+,\\d+")
        lines.get(6).matches("warm-up build #3,\\d+,\\d+")
        lines.get(7).matches("warm-up build #4,,\\d+")
        lines.get(8).matches("warm-up build #5,,\\d+")
        lines.get(9).matches("warm-up build #6,,\\d+")
        lines.get(10).matches("measured build #1,,\\d+")
        lines.get(11).matches("measured build #2,,\\d+")
        lines.get(19).matches("measured build #10,,\\d+")
    }

    def "recovers from failure to run any builds while running benchmarks"() {
        given:
        brokenBuild(0)

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--gradle-version", "5.0", "--benchmark", "assemble")

        then:
        def e = thrown(Main.ScenarioFailedException)
        logFile.containsOne(e.cause.message)
        output.contains(e.cause.message)
        logFile.containsOne("java.lang.RuntimeException: broken!")
        output.contains("java.lang.RuntimeException: broken!")

        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 2
        logFile.find("<gradle-version: 5.0>").size() == 17
        logFile.find("<tasks: [help]>").size() == 2
        logFile.find("<tasks: [assemble]>").size() == 1 + 16

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,Gradle ${minimalSupportedGradleVersion},Gradle 5.0"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,execution,execution"
        lines.get(4).matches("warm-up build #1,,\\d+")
        lines.get(5).matches("warm-up build #2,,\\d+")
        lines.get(6).matches("warm-up build #3,,\\d+")
        lines.get(10).matches("measured build #1,,\\d+")
        lines.get(11).matches("measured build #2,,\\d+")
        lines.get(19).matches("measured build #10,,\\d+")
    }

    def brokenBuild(int successfulIterations) {
        instrumentedBuildScript()
        buildFile << """
class Holder {
    static int counter
}

assemble.doFirst {
    if (gradle.gradleVersion == "${minimalSupportedGradleVersion}" && Holder.counter++ >= ${successfulIterations}) {
        throw new RuntimeException("broken!")
    }
}
"""

    }
}
