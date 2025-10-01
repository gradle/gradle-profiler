package org.gradle.profiler

class BuildOperationsTraceIntegrationTest extends AbstractProfilerIntegrationTest {

    def "can enable build operations trace via command-line flag"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--build-ops-trace", "--warmups", "1", "--iterations", "1", "help")

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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--warmups", "1", "--iterations", "1", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        logFile.containsOne("Build operations trace: ")
    }

    def "build operations trace is disabled by default"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--warmups", "1", "--iterations", "1", "help")

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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--benchmark", "--build-ops-trace", "--warmups", "1", "--iterations", "1", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        logFile.containsOne("Build operations trace: ")
    }
}
