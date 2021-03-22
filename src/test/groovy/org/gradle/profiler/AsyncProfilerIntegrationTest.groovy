package org.gradle.profiler

import spock.lang.Requires
import spock.lang.Unroll

@Unroll
class AsyncProfilerIntegrationTest extends AbstractProfilerIntegrationTest implements FlameGraphFixture {
    @Requires({ !OperatingSystem.isWindows() })
    def "profiles build CPU usage using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "async-profiler", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGenerated()
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles multiple events using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "async-profiler-all", "assemble"
        )

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGenerated("allocation", "cpu", "monitor-blocked")
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles wall clock time using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "async-profiler",
            "--async-profiler-event", "wall",
            "--async-profiler-event", "alloc",
            "assemble"
        )

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGenerated("allocation", "cpu")
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles heap allocation using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "async-profiler-heap", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        assertGraphsGenerated("allocation")
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles multiple iterations using #profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", profiler, "--iterations", "2", "assemble")

        then:
        logFile.find("<daemon: true").size() == 5
        logFile.containsOne("<invocations: 4>")

        and:
        assertGraphsGenerated()

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles build using #profiler with tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", profiler, "--cold-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 3
        logFile.find("<invocations: 1>").size() == 3

        and:
        assertGraphsGenerated()

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles build using #profiler with CLI and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", profiler, "--no-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        and:
        assertGraphsGenerated()

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles using #profiler with multiple iterations and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", profiler, "--iterations", "2", "--cold-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.find("<invocations: 1>").size() == 4

        and:
        assertGraphsGenerated()

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "profiles using #profiler with multiple iterations and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", profiler, "--iterations", "2", "--no-daemon", "assemble")

        then:
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 3
        logFile.find("<invocations: 1>").size() == 4

        and:
        assertGraphsGenerated()

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }

    @Requires({ !OperatingSystem.isWindows() })
    def "scenario name when using #profiler can contain reserved characters"() {
        given:
        instrumentedBuildScript()
        def scenarios = file("performance.scenarios")
        scenarios << """
            a/b {
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", profiler, "--scenario-file", scenarios.absolutePath, "a/b")

        then:
        logFile.find("<daemon: true").size() == 4
        logFile.containsOne("<invocations: 3>")

        and:
        ["raw", "simplified"].each { type ->
            assert new File(outputDir, "a-b/a-b-${latestSupportedGradleVersion}-cpu-${type}-flames.svg").file
            assert new File(outputDir, "a-b/a-b-${latestSupportedGradleVersion}-cpu-${type}-icicles.svg").file
        }

        where:
        profiler << ["async-profiler", "async-profiler-all"]
    }
}
