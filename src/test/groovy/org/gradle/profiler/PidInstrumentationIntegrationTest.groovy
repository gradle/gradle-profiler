package org.gradle.profiler

class PidInstrumentationIntegrationTest extends AbstractProfilerIntegrationTest {

    def "pid instrumentation works for cold daemon with configuration caching"() {
        given:
        instrumentedBuildScript()
        file("gradle.properties") << """
                org.gradle.unsafe.configuration-cache=true
            """
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                run-using = cli
                daemon = cold
                tasks = assemble
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        logFile.containsOne("Run using: `gradle` command with cold daemon")
        logFile.find("Configuration cache entry reused.").size() >= 1
        resultFile.containsColdDaemonScenario(latestSupportedGradleVersion, "s1", ["assemble"])
    }
}
