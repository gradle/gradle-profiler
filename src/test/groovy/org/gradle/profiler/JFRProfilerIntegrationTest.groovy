package org.gradle.profiler

import spock.lang.Unroll

class JFRProfilerIntegrationTest extends AbstractProfilerIntegrationTest {
    @Unroll
    def "can profile Gradle #versionUnderTest using JFR, tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 2
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $versionUnderTest>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        logFile.containsOne("<invocations: 3>")

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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--cli", "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 2
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $versionUnderTest>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        logFile.containsOne("<invocations: 3>")

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

    @Unroll
    def "can profile multiple iterations of Gradle #versionUnderTest using JFR, tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--iterations", "2", "assemble")

        then:
        // Probe version, 2 warm up, 2 builds
        logFile.containsOne("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 2
        logFile.find("* Running measured build").size() == 2
        logFile.find("<gradle-version: $versionUnderTest>").size() == 5
        logFile.find("<daemon: true").size() == 5
        logFile.find("<tasks: [assemble]>").size() == 4
        logFile.containsOne("<invocations: 4>")

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

    @Unroll
    def "can profile Gradle #versionUnderTest using JFR, tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--cold-daemon", "assemble")

        then:
        // Probe version, 1 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $versionUnderTest>").size() == 3
        logFile.find("<daemon: true").size() == 3
        logFile.find("<tasks: [assemble]>").size() == 2
        logFile.find("<invocations: 1>").size() == 3

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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr", "--cold-daemon", "--cli", "assemble")

        then:
        // Probe version, 1 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $versionUnderTest>").size() == 3
        logFile.find("<daemon: true").size() == 3
        logFile.find("<tasks: [assemble]>").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
    }

    @Unroll
    def "can profile Gradle no daemon #versionUnderTest with #iterations iterations"(String versionUnderTest, int iterations) {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", versionUnderTest,
            "--profile", "jfr",
            "--warmups", "1",
            "--iterations", iterations.toString(),
            "--no-daemon",
            "assemble")

        then:
        // Probe version, 1 warm up, 2 build
        logFile.containsOne("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == iterations
        logFile.find("<gradle-version: $versionUnderTest>").size() == 2 + iterations
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 1 + iterations
        logFile.find("<tasks: [assemble]>").size() == 1 + iterations
        logFile.find("<invocations: 1>").size() == 2 + iterations

        outputDir.listFiles().findAll { it.name.endsWith(".jfr") }.size() == iterations
        if (!OperatingSystem.isWindows()) {
            // No perl installed on Windows
            new File(outputDir, "${versionUnderTest}.jfr-flamegraphs").isDirectory()
        }
        where:
        versionUnderTest              | iterations
        minimalSupportedGradleVersion | 1
        minimalSupportedGradleVersion | 2
        latestSupportedGradleVersion  | 1
        latestSupportedGradleVersion  | 2
    }

    def "cannot profile using JFR with multiple iterations and cleanup steps"() {
        given:
        File scenarioFile = prepareBuild()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jfr", "--iterations", "2", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario assemble using Gradle ${minimalSupportedGradleVersion}: Profiler JFR does not support profiling multiple iterations with cleanup steps in between.")
    }

    def "can profile using JFR with multiple iterations and cleanup steps with no daemon"() {
        given:
        File scenarioFile = prepareBuild()

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jfr", "--iterations", "2", "--no-daemon", "assemble")

        then:
        new File(outputDir, "assemble").listFiles().findAll { it.name.endsWith(".jfr") }.size() == 2
    }

    private File prepareBuild() {
        instrumentedBuildScript()

        def scenarioFile = file("performance.scenarios")
        scenarioFile.text = """
            assemble {
                cleanup-tasks = "clean"
            }
        """
        return scenarioFile
    }
}
