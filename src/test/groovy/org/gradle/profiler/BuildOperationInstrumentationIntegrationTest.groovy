package org.gradle.profiler


class BuildOperationInstrumentationIntegrationTest extends AbstractProfilerIntegrationTest {
    def "can benchmark configuration time for build"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--benchmark-config-time", "assemble")

        then:
        logFile.grep("-> config time").size() == 10
    }
}
