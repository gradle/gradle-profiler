package org.gradle.profiler

import spock.lang.Requires
import spock.lang.Unroll

@Unroll
@Requires({ !OperatingSystem.isWindows() })
class DifferentialFlameGraphIntegrationTest extends AbstractProfilerIntegrationTest implements FlameGraphFixture {
    def "generates differential flame graphs with #profiler"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--gradle-version", minimalSupportedGradleVersion,
            "--iterations", "3",
            "--profile", profiler,
            "assemble")

        then:
        logFile.find("<daemon: true").size() == 12
        logFile.find("<invocations: 3>").size() == 2

        and:
        assertGraphsGeneratedForVersions(latestSupportedGradleVersion, minimalSupportedGradleVersion)
        assertDifferentialGraphsGenerated([latestSupportedGradleVersion, minimalSupportedGradleVersion])

        where:
        profiler << ["async-profiler", "jfr"]
    }

    def "generates differential flame graphs with #profiler for scenario file"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios")
        scenarioFile << """
            upToDate {
                tasks = ["assemble"]
            }
        """.stripIndent()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--gradle-version", minimalSupportedGradleVersion,
            "--scenario-file", scenarioFile.absolutePath,
            "--iterations", "3",
            "--profile", profiler,
            "upToDate"
        )

        then:
        logFile.find("<daemon: true").size() == 12
        logFile.find("<invocations: 3>").size() == 2

        and:
        assertGraphsGenerated(['upToDate'], [latestSupportedGradleVersion, minimalSupportedGradleVersion], ['cpu'])
        assertDifferentialGraphsGenerated(['upToDate'], [latestSupportedGradleVersion, minimalSupportedGradleVersion])


        where:
        profiler << ["async-profiler", "jfr"]
    }

    def "generates differential flame graphs with #profiler for cross-build scenarios"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios")
        scenarioFile << """
            upToDate {
                tasks = ["assemble"]
            }
            help {
                tasks = ["help"]
            }
        """.stripIndent()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion,
            "--scenario-file", scenarioFile.absolutePath,
            "--iterations", "3",
            "--profile", profiler,
            "upToDate", "help"
        )

        then:
        logFile.find("<daemon: true").size() == 11
        logFile.find("<invocations: 3>").size() == 2

        and:
        assertGraphsGeneratedForScenarios('upToDate', 'help')
        assertDifferentialGraphsGenerated(['upToDate', 'help'], [latestSupportedGradleVersion])


        where:
        profiler << ["async-profiler", "jfr"]
    }

    def "can disable generation of differential flame graphs"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--gradle-version", minimalSupportedGradleVersion,
            "--no-diffs",
            "--iterations", "3",
            "--profile", "async-profiler",
            "assemble")

        then:
        logFile.find("<daemon: true").size() == 12
        logFile.find("<invocations: 3>").size() == 2

        and:
        assertGraphsGeneratedForVersions(latestSupportedGradleVersion, minimalSupportedGradleVersion)
        assertNoDifferentialFlameGraphsGenerated()
    }
}
