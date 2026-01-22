package org.gradle.profiler


import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest
import spock.lang.Requires

@Requires({ !OperatingSystem.isWindows() })
class AsyncProfilerGradleCrossVersionTest extends AbstractGradleCrossVersionTest implements FlameGraphFixture {
    def setup() {
        defaultWarmupsAndIterations()
    }

    def "profiles build CPU usage using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", gradleVersion,
            "--iterations", "3",
            "--profile", "async-profiler", "assemble"
        ])

        then:
        logFile.find("<daemon: true").size() == 6
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGeneratedForScenario(gradleVersion)
    }

    def "profiles multiple events using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", gradleVersion,
            "--iterations", "3",
            "--profile", "async-profiler-all",
            "assemble"
        ])

        then:
        logFile.find("<daemon: true").size() == 6
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGeneratedForScenario(gradleVersion, ["allocation", "cpu", "monitor-blocked"])
        if (!OperatingSystem.isMacOS()) {
            assertGraphsGeneratedForScenario(gradleVersion, ["wall"])
        }
    }

    def "profiles wall clock time using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", gradleVersion, "--profile", "async-profiler",
            "--iterations", "3",
            "--async-profiler-event", "wall",
            "--async-profiler-event", "alloc",
            "assemble"
        ])

        then:
        logFile.find("<daemon: true").size() == 6
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGeneratedForScenario(gradleVersion, ["allocation", "wall"]) // Since async-profiler 4.0, wall clock have their own event
    }

    def "profiles wall clock time allocation using async-profiler with tooling API and warm daemon using async-profiler-wall option"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--iterations", "3",
            "--gradle-version", gradleVersion,
            "--profile", "async-profiler-wall", "assemble"
        ])

        then:
        logFile.find("<daemon: true").size() == 6
        logFile.containsOne("<invocations: 3>")

        and:
        def expectedEvents = OperatingSystem.isMacOS() ? ["wall"] : ["cpu", "wall"]
        assertGraphsGeneratedForScenario(gradleVersion, expectedEvents)
    }

    def "profiles heap allocation using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--iterations", "3",
            "--gradle-version", gradleVersion,
            "--profile", "async-profiler-heap", "assemble"
        ])

        then:
        logFile.find("<daemon: true").size() == 6
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGeneratedForScenario(gradleVersion, ["allocation"])
    }

    def "profiles multiple iterations using #profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", profiler, "--iterations", "3", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 6
        logFile.containsOne("<invocations: 4>")

        and:
        assertGraphsGeneratedForScenario(gradleVersion)

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    def "profiles build using #profiler with tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", profiler, "--cold-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        assertGraphsGeneratedForScenario(gradleVersion)

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    def "profiles build using #profiler with CLI and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", profiler, "--no-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        assertGraphsGeneratedForScenario(gradleVersion)

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    def "profiles using #profiler with multiple iterations and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", profiler, "--iterations", "2", "--cold-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.find("<invocations: 1>").size() == 4

        and:
        assertGraphsGeneratedForScenario(gradleVersion)

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    def "profiles using #profiler with multiple iterations and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", profiler, "--iterations", "2", "--no-daemon", "assemble"])

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 3
        logFile.find("<invocations: 1>").size() == 4

        and:
        assertGraphsGeneratedForScenario(gradleVersion)

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    def "scenario name when using #profiler can contain reserved characters"() {
        given:
        instrumentedBuildScript()
        def scenarios = file("performance.scenarios")
        scenarios << """
            a/b {
            }
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--iterations", "3",
            "--profile", profiler,
            "--scenario-file", scenarios.absolutePath, "a/b"
        ])

        then:
        logFile.find("<daemon: true").size() == 6
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGenerated(['a-b'], [gradleVersion], ['cpu'])

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }
}
