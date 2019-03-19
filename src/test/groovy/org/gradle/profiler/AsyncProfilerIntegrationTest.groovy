package org.gradle.profiler

import org.gradle.profiler.asyncprofiler.AsyncProfiler
import spock.lang.Requires

class AsyncProfilerIntegrationTest extends AbstractProfilerIntegrationTest {
    @Requires({ AsyncProfilerIntegrationTest.asyncProfilerHome() })
    def "profiles build using async-profiler with tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "async-profiler", "assemble")

        then:
        logFile.grep("<daemon: true").size() == 4
        logFile.contains("<invocations: 3>")

        and:
        new File(outputDir, "${minimalSupportedGradleVersion}-flames.svg").file
        new File(outputDir, "${minimalSupportedGradleVersion}-icicles.svg").file
    }

    @Requires({ AsyncProfilerIntegrationTest.asyncProfilerHome() })
    def "profiles build using async-profiler with tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "async-profiler", "--cold-daemon", "assemble")

        then:
        logFile.grep("<daemon: true").size() == 3
        logFile.grep("<invocations: 1>").size() == 3

        and:
        new File(outputDir, "${minimalSupportedGradleVersion}-flames.svg").file
        new File(outputDir, "${minimalSupportedGradleVersion}-icicles.svg").file
    }

    @Requires({ AsyncProfilerIntegrationTest.asyncProfilerHome() })
    def "profiles build using async-profiler with tooling API and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "async-profiler", "--no-daemon", "assemble")

        then:
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 2
        logFile.grep("<invocations: 1>").size() == 3

        and:
        new File(outputDir, "${minimalSupportedGradleVersion}-flames.svg").file
        new File(outputDir, "${minimalSupportedGradleVersion}-icicles.svg").file
    }

    static File asyncProfilerHome() {
        def homeDir = System.getenv(AsyncProfiler.ASYNC_PROFILER_HOME)
        return homeDir ? new File(homeDir) : null
    }
}
