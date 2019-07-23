package org.gradle.profiler

import spock.lang.Unroll


class BuildOperationInstrumentationIntegrationTest extends AbstractProfilerIntegrationTest {
    @Unroll
    def "can benchmark configuration time for build using #gradleVersion"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--benchmark", "--benchmark-config-time", "assemble")

        then:
        def lines = resultFile.lines
        lines.size() == 27 // 4 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,${gradleVersion},${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,execution,task start"
        lines.get(4).matches("warm-up build #1,\\d+,\\d+")
        lines.get(9).matches("warm-up build #6,\\d+,\\d+")
        lines.get(10).matches("measured build #1,\\d+,\\d+")
        lines.get(20).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(23).matches("median,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(26).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+")

        where:
        gradleVersion << ["5.0", latestSupportedGradleVersion]
    }

    @Unroll
    def "can benchmark snapshotting build operation time via #via for build using 5.5.1"() {
        given:
        instrumentedBuildScript()
        buildFile << """
            apply plugin: 'java'
        """

        // We don't capture snapshotting time (yet) if the build cache is not enabled
        new File(projectDir, "gradle.properties").text = "org.gradle.caching=true"

        def sourceFile = new File(projectDir, "src/main/java/A.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "class A {}"

        def gradleVersion = "5.5.1"

        when:
        def extraArgs = []
        if (scenarioConfiguration) {
            def scenarioFile = new File(projectDir, "performance.scenarios")
            scenarioFile.text = """
            default {
                tasks = ["assemble"]
                ${scenarioConfiguration}
            }
            """
            extraArgs.addAll("--scenario-file", scenarioFile.absolutePath)
        }
        extraArgs.addAll(commandLine)
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--benchmark",
            *extraArgs,
            scenarioConfiguration ? "default" : "assemble"
        )

        then:
        def lines = resultFile.lines
        lines.size() == 27 // 4 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,${gradleVersion},${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,execution,org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"

        def firstWarmup = lines.get(4)
        def snapshottingDuration = firstWarmup =~ /warm-up build #1,\d+,(\d+)/
        snapshottingDuration.matches()
        Long.valueOf(snapshottingDuration[0][1]) > 0

        lines.get(9).matches("warm-up build #6,\\d+,\\d+")
        lines.get(10).matches("measured build #1,\\d+,\\d+")
        lines.get(20).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(23).matches("median,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(26).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+")

        where:
        via                              | commandLine                                                                                  | scenarioConfiguration
        'command line'                   | ["--measure-build-op", "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"] | null
        'scenario file'                  | []                                                                                           | 'measured-build-ops = ["org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"]'
        'command line and scenario file' | ["--measure-build-op", "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"] | 'measured-build-ops = ["org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"]'
    }

    @Unroll
    def "complains when attempting to benchmark configuration time for build using #gradleVersion"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--benchmark", "--benchmark-config-time", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario using Gradle ${gradleVersion}: Measuring build configuration is only supported for Gradle 5.0 and later")

        where:
        gradleVersion << [minimalSupportedGradleVersion, "4.0", "4.10"]
    }

    def "complains when attempting to benchmark configuration time for build using unsupported Gradle version from scenario file"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios")
        scenarioFile.text = """
            assemble { 
                versions = ["${minimalSupportedGradleVersion}", "4.0", "4.10", "${latestSupportedGradleVersion}"]
            }
        """

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "--benchmark-config-time", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario assemble using Gradle ${minimalSupportedGradleVersion}: Measuring build configuration is only supported for Gradle 5.0 and later")
        output.contains("Scenario assemble using Gradle 4.0: Measuring build configuration is only supported for Gradle 5.0 and later")
        output.contains("Scenario assemble using Gradle 4.10: Measuring build configuration is only supported for Gradle 5.0 and later")
    }
}
