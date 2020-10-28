package org.gradle.profiler

import spock.lang.Unroll

@Unroll
class GradleInvocationIntegrationTest extends AbstractProfilerIntegrationTest {
    def "benchmarks using tooling API and warm daemon when invocation type is not specified"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "assemble")

        then:
        logFile.containsOne("Run using: Tooling API")
        logFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
        resultFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
    }

    def "benchmarks when scenario specifies using tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                run-using = tooling-api
                tasks = assemble
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "s1")

        then:
        logFile.containsOne("Run using: Tooling API")
        logFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
        resultFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using `gradle` command and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--cli", "assemble")

        then:
        logFile.containsOne("Run using: `gradle` command")
        logFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
        resultFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
    }

    def "benchmarks when scenario specifies using `gradle` command and warm daemon"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                run-using = cli
                tasks = assemble
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        logFile.containsOne("Run using: `gradle` command")
        logFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
        resultFile.containsWarmDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--cold-daemon", "assemble")

        then:
        logFile.containsOne("Run using: Tooling API with cold daemon")
        logFile.containsColdDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
        resultFile.containsColdDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
    }

    def "benchmarks when scenario specifies using tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                daemon = cold
                tasks = assemble
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        logFile.containsOne("Run using: Tooling API with cold daemon")
        logFile.containsColdDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
        resultFile.containsColdDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using `gradle` command and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--cold-daemon", "--cli", "assemble")

        then:
        logFile.containsOne("Run using: `gradle` command with cold daemon")
        logFile.containsColdDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
        resultFile.containsColdDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
    }

    def "benchmarks when scenario specifies using `gradle` command and cold daemon"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                run-using = cli
                daemon = cold
                tasks = assemble
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        logFile.containsOne("Run using: `gradle` command with cold daemon")
        logFile.containsColdDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
        resultFile.containsColdDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--no-daemon", "assemble")

        then:
        logFile.containsOne("Run using: `gradle` command with --no-daemon")
        logFile.containsNoDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
        resultFile.containsNoDaemonScenario(minimalSupportedGradleVersion, ["assemble"])
    }

    def "benchmarks when scenario specifies using `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                daemon = none
                tasks = assemble
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        logFile.containsOne("Run using: `gradle` command with --no-daemon")
        logFile.containsNoDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
        resultFile.containsNoDaemonScenario(minimalSupportedGradleVersion, "s1", ["assemble"])
    }

    def "can pass jvm args with spaces to #invokerString and #daemon daemon"() {
        given:
        instrumentedBuildScript()
        buildFile << """
            println("mySystemProperty='\${System.getProperty("mySystemProperty")}'")
        """
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                run-using = ${runUsing}
                daemon = $daemon
                tasks = assemble
                jvm-args = ["-DmySystemProperty=I have spaces!", "-Xms128m"]
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--warmups", "2", "--iterations", "2", "--scenario-file", scenarioFile.absolutePath, "s1")

        then:
        !logFile.find("mySystemProperty='I have spaces!'").empty

        where:
        runUsing      | daemon
        "tooling-api" | "warm"
        "tooling-api" | "cold"
        "cli"         | "warm"
        "cli"         | "cold"
        "cli"         | "none"

        invokerString = runUsing == "cli" ? "`gradle` command" : "tooling API"
    }
}
