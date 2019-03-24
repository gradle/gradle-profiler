package org.gradle.profiler


class BuildOperationInstrumentationIntegrationTest extends AbstractProfilerIntegrationTest {
    def "can benchmark configuration time for build"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--benchmark-config-time", "assemble")

        then:
        def lines = resultFile.lines
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default,"
        lines.get(1) == "version,${latestSupportedGradleVersion},"
        lines.get(2) == "tasks,assemble,"
        lines.get(3).matches("warm-up build #1,\\d+,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+,\\d+")
        lines.get(9).matches("measured build #1,\\d+,\\d+")
    }
}
