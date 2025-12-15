package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest

class JFRProfilerGradleCrossVersionTest extends AbstractGradleCrossVersionTest {
    def setup() {
        defaultWarmupsAndIterations()
    }

    def "can profile using JFR, tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "jfr", "assemble"])

        then:
        // Probe version, 2 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $gradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 2
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $gradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        logFile.containsOne("<invocations: 3>")

        def profileFile = new File(outputDir, "${gradleVersion}.jfr")
        profileFile.exists()
    }

    def "can profile using JFR, `gradle` command and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "jfr", "--cli", "assemble"])

        then:
        // Probe version, 2 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $gradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 2
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $gradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        logFile.containsOne("<invocations: 3>")

        def profileFile = new File(outputDir, "${gradleVersion}.jfr")
        profileFile.exists()
    }

    def "can profile multiple iterations using JFR, tooling API and warm daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--iterations", "2", "--profile", "jfr", "assemble"])

        then:
        // Probe version, 2 warm up, 2 builds
        logFile.containsOne("* Running scenario using Gradle $gradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 2
        logFile.find("* Running measured build").size() == 2
        logFile.find("<gradle-version: $gradleVersion>").size() == 5
        logFile.find("<daemon: true").size() == 5
        logFile.find("<tasks: [assemble]>").size() == 4
        logFile.containsOne("<invocations: 4>")

        def profileFile = new File(outputDir, "${gradleVersion}.jfr")
        profileFile.exists()
    }

    def "can profile using JFR, tooling API and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "jfr", "--cold-daemon", "assemble"])

        then:
        // Probe version, 1 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $gradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $gradleVersion>").size() == 3
        logFile.find("<daemon: true").size() == 3
        logFile.find("<tasks: [assemble]>").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        def profileFile = new File(outputDir, "${gradleVersion}.jfr")
        profileFile.exists()
    }

    def "can profile using JFR, `gradle` command and cold daemon"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "jfr", "--cold-daemon", "--cli", "assemble"])

        then:
        // Probe version, 1 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $gradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == 1
        logFile.find("<gradle-version: $gradleVersion>").size() == 3
        logFile.find("<daemon: true").size() == 3
        logFile.find("<tasks: [assemble]>").size() == 2
        logFile.find("<invocations: 1>").size() == 3

        def profileFile = new File(outputDir, "${gradleVersion}.jfr")
        profileFile.exists()
    }

    def "can profile using JFR with #iterationCount iterations"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", gradleVersion, "--profile", "jfr", "--iterations", iterationCount.toString(), "--cold-daemon", "--cli", "assemble"])

        def jfrFileDirectory = findJfrDirectory(iterationCount)

        then:
        // Probe version, 1 warm up, <iteration> builds
        logFile.containsOne("* Running scenario using Gradle $gradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == iterationCount
        logFile.find("<invocations: 1>").size() == 2 + iterationCount

        jfrFileDirectory.listFiles().findAll { it.name.endsWith(".jfr") }.size() == iterationCount

        where:
        iterationCount << [1, 2]
    }

    def "can profile no daemon with #interactionCount iterations"() {
        given:
        instrumentedBuildScript()

        when:
        run([
            "--gradle-version", gradleVersion,
            "--profile", "jfr",
            "--warmups", "1",
            "--iterations", iterationCount.toString(),
            "--no-daemon",
            "assemble"])

        def jfrFileDirectory = findJfrDirectory(iterationCount)

        then:
        // Probe version, 1 warm up, 2 build
        logFile.containsOne("* Running scenario using Gradle $gradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 1
        logFile.find("* Running measured build").size() == iterationCount
        logFile.find("<gradle-version: $gradleVersion>").size() == 2 + iterationCount
        logFile.find("<daemon: true").size() == 1
        logFile.find("<daemon: false").size() == 1 + iterationCount
        logFile.find("<tasks: [assemble]>").size() == 1 + iterationCount
        logFile.find("<invocations: 1>").size() == 2 + iterationCount

        jfrFileDirectory.listFiles().findAll { it.name.endsWith(".jfr") }.size() == iterationCount
        // No perl installed on Windows
        if (!OperatingSystem.isWindows()) {
            // Events: alloc, cpu / Type: raw, simplified
            // Looks like monitor-locked and io mostly aren't captured
            int numberOfFlames = 2 * 2
            assert outputDir.listFiles().findAll { it.name.endsWith("-flames.svg") }.size() >= numberOfFlames
            assert outputDir.listFiles().findAll { it.name.endsWith("-icicles.svg") }.size() >= numberOfFlames
            assert outputDir.listFiles().findAll { it.name.endsWith("-stacks.txt") }.size() >= numberOfFlames
        }
        where:
        iterationCount << [1, 2]
    }

    def "cannot profile using JFR with multiple iterations and cleanup steps"() {
        given:
        iterations = 2
        File scenarioFile = prepareBuild()

        when:
        run(["--scenario-file", scenarioFile.absolutePath, "--gradle-version", gradleVersion, "--profile", "jfr", "assemble"])

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario assemble using Gradle ${gradleVersion}: Profiler JFR does not support profiling multiple iterations with cleanup steps in between.")
    }

    def "can profile using JFR with multiple iterations and cleanup steps with no daemon"() {
        given:
        iterations = 2
        File scenarioFile = prepareBuild()

        when:
        run(["--scenario-file", scenarioFile.absolutePath, "--gradle-version", gradleVersion, "--profile", "jfr", "--no-daemon", "assemble"])

        then:
        findJfrDirectory(2, outputDir).listFiles().findAll { it.name.endsWith(".jfr") }.size() == 2
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

    private File findJfrDirectory(int iterations, File outputDir = this.outputDir) {
        return iterations == 1 ? outputDir : outputDir.listFiles().find { it.name.endsWith("-jfr") }
    }
}
