package org.gradle.profiler

import spock.lang.Unroll

class JFRProfilerIntegrationTest extends AbstractProfilerIntegrationTest {
    @Unroll
    def "can profile Gradle #versionUnderTest using JFR, tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.contains("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 2
        logFile.grep("* Running measured build").size() == 1
        logFile.grep("* Starting profiler for daemon with pid").size() == 1
        logFile.grep("<gradle-version: $versionUnderTest>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
        logFile.contains("<invocations: 3>")

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

    @Unroll
    def "can profile Gradle #versionUnderTest using JFR, `gradle` command and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--cli", "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.contains("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 2
        logFile.grep("* Running measured build").size() == 1
        logFile.grep("* Starting profiler for daemon with pid").size() == 1
        logFile.grep("<gradle-version: $versionUnderTest>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
        logFile.contains("<invocations: 3>")

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

    @Unroll
    def "can profile Gradle #versionUnderTest using JRF, tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--cold-daemon", "assemble")

        then:
        // Probe version, 1 warm up, 1 build
        logFile.contains("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 1
        logFile.grep("* Running measured build").size() == 1
        logFile.grep("* Starting profiler for daemon with pid").size() == 1
        logFile.grep("<gradle-version: $versionUnderTest>").size() == 3
        logFile.grep("<daemon: true").size() == 3
        logFile.grep("<tasks: [assemble]>").size() == 2
        logFile.grep("<invocations: 1>").size() == 3

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

    @Unroll
    def "can profile Gradle #versionUnderTest using JFR, `gradle` command and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--cold-daemon", "--cli", "assemble")

        then:
        // Probe version, 1 warm up, 1 build
        logFile.contains("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 1
        logFile.grep("* Running measured build").size() == 1
        logFile.grep("* Starting profiler for daemon with pid").size() == 1
        logFile.grep("<gradle-version: $versionUnderTest>").size() == 3
        logFile.grep("<daemon: true").size() == 3
        logFile.grep("<tasks: [assemble]>").size() == 2
        logFile.grep("<invocations: 1>").size() == 3

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

    @Unroll
    def "can profile Gradle #versionUnderTest using JFR, `gradle` command and no daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--no-daemon", "assemble")

        then:
        // Probe version, 1 warm up, 1 build
        logFile.contains("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 1
        logFile.grep("* Running measured build").size() == 1
        logFile.grep("* Starting profiler for daemon with pid").size() == 1
        logFile.grep("<gradle-version: $versionUnderTest>").size() == 3
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 2
        logFile.grep("<tasks: [assemble]>").size() == 2
        logFile.grep("<invocations: 1>").size() == 3

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

}
