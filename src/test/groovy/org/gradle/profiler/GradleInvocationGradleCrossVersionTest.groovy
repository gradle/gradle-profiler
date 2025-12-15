package org.gradle.profiler


import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest

class GradleInvocationGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    def setup() {
        defaultWarmupsAndIterations()
    }

    def "benchmarks using tooling API and warm daemon when invocation type is not specified"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "assemble"])

        then:
        logFile.containsOne("Run using: Tooling API")
        logFile.containsWarmDaemonScenario(gradleVersion, ["assemble"])
        resultFile.containsWarmDaemonScenario(gradleVersion, ["assemble"])
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
        run(["--gradle-version", gradleVersion, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "s1"])

        then:
        logFile.containsOne("Run using: Tooling API")
        logFile.containsWarmDaemonScenario(gradleVersion, "s1", ["assemble"])
        resultFile.containsWarmDaemonScenario(gradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using `gradle` command and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "--cli", "assemble"])

        then:
        logFile.containsOne("Run using: `gradle` command")
        logFile.containsWarmDaemonScenario(gradleVersion, ["assemble"])
        resultFile.containsWarmDaemonScenario(gradleVersion, ["assemble"])
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
        run(["--gradle-version", gradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1"])

        then:
        logFile.containsOne("Run using: `gradle` command")
        logFile.containsWarmDaemonScenario(gradleVersion, "s1", ["assemble"])
        resultFile.containsWarmDaemonScenario(gradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "--cold-daemon", "assemble"])

        then:
        logFile.containsOne("Run using: Tooling API with cold daemon")
        logFile.containsColdDaemonScenario(gradleVersion, ["assemble"])
        resultFile.containsColdDaemonScenario(gradleVersion, ["assemble"])
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
        run(["--gradle-version", gradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1"])

        then:
        logFile.containsOne("Run using: Tooling API with cold daemon")
        logFile.containsColdDaemonScenario(gradleVersion, "s1", ["assemble"])
        resultFile.containsColdDaemonScenario(gradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using `gradle` command and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "--cold-daemon", "--cli", "assemble"])

        then:
        logFile.containsOne("Run using: `gradle` command with cold daemon")
        logFile.containsColdDaemonScenario(gradleVersion, ["assemble"])
        resultFile.containsColdDaemonScenario(gradleVersion, ["assemble"])
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
        run(["--gradle-version", gradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1"])

        then:
        logFile.containsOne("Run using: `gradle` command with cold daemon")
        logFile.containsColdDaemonScenario(gradleVersion, "s1", ["assemble"])
        resultFile.containsColdDaemonScenario(gradleVersion, "s1", ["assemble"])
    }

    def "can benchmark using `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--benchmark", "--no-daemon", "assemble"])

        then:
        logFile.containsOne("Run using: `gradle` command with --no-daemon")
        logFile.containsNoDaemonScenario(gradleVersion, ["assemble"])
        resultFile.containsNoDaemonScenario(gradleVersion, ["assemble"])
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
        run(["--gradle-version", gradleVersion, "--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1"])

        then:
        logFile.containsOne("Run using: `gradle` command with --no-daemon")
        logFile.containsNoDaemonScenario(gradleVersion, "s1", ["assemble"])
        resultFile.containsNoDaemonScenario(gradleVersion, "s1", ["assemble"])
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
        run(["--gradle-version", gradleVersion, "--benchmark", "--warmups", "2", "--iterations", "2", "--scenario-file", scenarioFile.absolutePath, "s1"])

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
