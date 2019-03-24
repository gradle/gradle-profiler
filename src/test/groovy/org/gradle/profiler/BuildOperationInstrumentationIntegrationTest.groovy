package org.gradle.profiler


class BuildOperationInstrumentationIntegrationTest extends AbstractProfilerIntegrationTest {
    def "can benchmark configuration time for build"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--benchmark-config-time", "assemble")

        then:
        def lines = resultFile.lines
        lines.size() == 27 // 4 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,${latestSupportedGradleVersion},${latestSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,execution,task start"
        lines.get(4).matches("warm-up build #1,\\d+,\\d+")
        lines.get(9).matches("warm-up build #6,\\d+,\\d+")
        lines.get(10).matches("measured build #1,\\d+,\\d+")
        lines.get(20).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(23).matches("median,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(26).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+")
    }
}
