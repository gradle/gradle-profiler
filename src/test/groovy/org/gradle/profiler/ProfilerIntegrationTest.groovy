package org.gradle.profiler

import org.gradle.profiler.buildscan.BuildScanProfiler
import org.gradle.util.GradleVersion
import spock.lang.Requires
import spock.lang.Unroll

@Unroll
class ProfilerIntegrationTest extends AbstractProfilerIntegrationTest {

    def "complains when neither profile or benchmark requested"() {
        when:
        new Main().run("--project-dir", projectDir.absolutePath)

        then:
        thrown(CommandLineParser.SettingsNotAvailableException)

        and:
        output.contains("Neither --profile or --benchmark specified.")
    }

    def "complains when scenario file contains unexpected entry"() {
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    gradle-version = 3.2
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--profile", "jfr")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Unrecognized key 'assemble.gradle-version' defined in scenario file " + scenarioFile)
    }

    def "complains when unknown scenario requested"() {
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    tasks = "assemble"
}
help {
    tasks = "help"
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--profile", "jfr", "asmbl")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Unknown scenario 'asmbl' requested. Available scenarios are: assemble, help")
    }

    def "complains when profiling and skipping warm-up builds"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jfr", "--warmups", "0", "--iterations", "2", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario using Gradle ${minimalSupportedGradleVersion}: You can not skip warm-ups when profiling or benchmarking a Gradle build. Use --no-daemon or --cold-daemon if you want to profile or benchmark JVM startup")
    }

    def "complains when benchmarking scenario and skipping warm-up builds"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    tasks = assemble
}
help {
    tasks = help
    warm-ups = 0
}
"""
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--iterations", "2", "--scenario-file", scenarioFile.absolutePath)

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario help using Gradle ${minimalSupportedGradleVersion}: You can not skip warm-ups when profiling or benchmarking a Gradle build. Use --no-daemon or --cold-daemon if you want to profile or benchmark JVM startup")
    }

    def "reports build failures"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
assemble.doFirst {
    throw new RuntimeException("broken!")
}
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jfr",
                "assemble")

        then:
        def e = thrown(Main.ScenarioFailedException)

        and:
        logFile.containsOne("ERROR: failed to run build. See log file for details.")
        output.contains("ERROR: failed to run build. See log file for details.")
        logFile.containsOne(e.cause.message)
        output.contains(e.cause.message)
        logFile.containsOne("java.lang.RuntimeException: broken!")
        output.contains("java.lang.RuntimeException: broken!")
    }

    def "profiles build using JFR, specified Gradle versions and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--gradle-version", "3.0", "--profile", "jfr", "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.containsOne("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.containsOne("* Running scenario using Gradle 3.0 (scenario 2/2)")
        logFile.find("<gradle-version: $minimalSupportedGradleVersion").size() == 4
        logFile.find("<gradle-version: 3.0").size() == 4

        new File(outputDir, "$minimalSupportedGradleVersion/${minimalSupportedGradleVersion}.jfr").file
        new File(outputDir, "3.0/3.0.jfr").file
    }

    def "can specify the number of warm-up builds and iterations when profiling"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jfr",
                "--warmups", "3", "--iterations", "2", "assemble")

        then:
        // Probe version, 3 warm up, 2 builds
        logFile.containsOne("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.find("* Running warm-up build").size() == 3
        logFile.find("* Running measured build").size() == 2
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 6
        logFile.find("<tasks: [assemble]>").size() == 5

        def profileFile = new File(outputDir, "${minimalSupportedGradleVersion}.jfr")
        profileFile.exists()
    }

    def "profiles build using Build Scans, specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "buildscan",
                "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished(BuildScanProfiler.defaultBuildScanVersion(GradleVersion.version(minimalSupportedGradleVersion)))
    }

    def "profiles build using Build Scans with latest supported Gradle version"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
"""

        when:
        new Main().
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", latestSupportedGradleVersion, "--profile", "buildscan",
                        "assemble")

        then:
        logFile.find("<gradle-version: $latestSupportedGradleVersion>").size() == 4
        assertBuildScanPublished(BuildScanProfiler.defaultBuildScanVersion(GradleVersion.version(latestSupportedGradleVersion)))
    }

    def "uses build scan version used by the build if present"() {
        given:
        buildFile.text = """
plugins {
    id 'com.gradle.build-scan' version '1.16'
}
apply plugin: BasePlugin

buildScan { termsOfServiceUrl = 'https://gradle.com/terms-of-service'; termsOfServiceAgree = 'yes' }

println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "buildscan",
                "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished()
    }

    def "profiles build using Build Scans overridden version specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
                "--profile", "buildscan", "--buildscan-version", "1.2",
                "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished("1.2")
    }

    def "profiles build using JFR, Build Scans, specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
                "--profile", "buildscan", "--buildscan-version", "1.2",
                "--profile", "jfr",
                "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished("1.2")

        def profileFile = new File(outputDir, "${minimalSupportedGradleVersion}.jfr")
        profileFile.isFile()
    }

    def "runs benchmarks using scenarios defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    versions = ["3.0", "$minimalSupportedGradleVersion"]
    tasks = assemble
}
help {
    versions = "$minimalSupportedGradleVersion"
    tasks = [help]
    daemon = none
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark")

        then:
        // Probe version, 2 scenarios have 6 warm up, 10 builds, 1 scenario has 1 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 1 + 16 + 11
        logFile.find("<gradle-version: 3.0").size() == 17
        logFile.find("<daemon: true").size() == 2 + 16 * 2
        logFile.find("<daemon: false").size() == 11
        logFile.find("<tasks: [help]>").size() == 2 + 11
        logFile.find("<tasks: [assemble]>").size() == 16 * 2

        logFile.containsOne("* Running scenario assemble using Gradle 3.0 (scenario 1/3)")
        logFile.containsOne("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 2/3)")
        logFile.containsOne("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 3/3)")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,assemble,assemble,help"
        lines.get(1) == "version,Gradle 3.0,Gradle ${minimalSupportedGradleVersion},Gradle ${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble,assemble,help"
        lines.get(3) == "value,execution,execution,execution"
    }

    def "runs benchmarks using scenario provided on command line and defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
xyz {
    versions = ["$minimalSupportedGradleVersion"]
}
doNotRun {
    versions = "$minimalSupportedGradleVersion"
    tasks = [broken]
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark", "xyz")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.find("<daemon: true").size() == 17
        logFile.find("<tasks: [help]>").size() == 1
        logFile.find("<tasks: []>").size() == 16

        logFile.containsOne("* Running scenario xyz using Gradle $minimalSupportedGradleVersion (scenario 1/1)")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,xyz"
        lines.get(1) == "version,Gradle ${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,default tasks"
        lines.get(3) == "value,execution"
    }

    def "profiles scenarios defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    tasks = assemble
}
help {
    tasks = help
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--profile", "jfr", "--gradle-version", minimalSupportedGradleVersion)

        then:
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 7
        logFile.find("<tasks: [help]>").size() == 4
        logFile.find("<tasks: [assemble]>").size() == 3

        logFile.containsOne("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.containsOne("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 2/2)")

        new File(outputDir, "assemble/assemble-${minimalSupportedGradleVersion}.jfr").file
        new File(outputDir, "help/help-${minimalSupportedGradleVersion}.jfr").file
    }

    def "runs benchmarks fetching tooling model"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
ideaModel {
    versions = ["$minimalSupportedGradleVersion", "$latestSupportedGradleVersion"]
    model = "org.gradle.tooling.model.idea.IdeaProject"
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
plugins.withId("idea") {
    // most likely due to IDEA model builder
    println("<idea>")
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark", "ideaModel")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.find("<gradle-version: $latestSupportedGradleVersion").size() == 17
        logFile.find("<daemon: true").size() == 17 * 2
        logFile.find("<tasks: []>").size() == 16 * 2
        logFile.find("<idea>").size() == 16 * 2

        logFile.containsOne("* Running scenario ideaModel using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.containsOne("* Running scenario ideaModel using Gradle $latestSupportedGradleVersion (scenario 2/2)")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,ideaModel,ideaModel"
        lines.get(1) == "version,Gradle $minimalSupportedGradleVersion,Gradle $latestSupportedGradleVersion"
        lines.get(2) == "tasks,model IdeaProject,model IdeaProject"
        lines.get(3) == "value,execution,execution"
    }

    def "profiles fetching tooling model using JFR"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
ideaModel {
    versions = ["$minimalSupportedGradleVersion", "$latestSupportedGradleVersion"]
    model = "org.gradle.tooling.model.idea.IdeaProject"
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
plugins.withId("idea") {
    // most likely due to IDEA model builder
    println("<idea>")
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--profile", "jfr", "ideaModel")

        then:
        // Probe version, 2 warm up, 1 profiled build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.find("<gradle-version: $latestSupportedGradleVersion>").size() == 4
        logFile.find("<daemon: true").size() == 8
        logFile.find("<tasks: []>").size() == 6
        logFile.find("<idea>").size() == 6

        logFile.containsOne("* Running scenario ideaModel using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.containsOne("* Running scenario ideaModel using Gradle $latestSupportedGradleVersion (scenario 2/2)")

        def profileFile = new File(outputDir, "ideaModel/$minimalSupportedGradleVersion/ideaModel-${minimalSupportedGradleVersion}.jfr")
        profileFile.isFile()
    }

    def "profiles scenarios defined in scenario file using multiple Gradle versions"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    tasks = assemble
}
help {
    tasks = help
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--profile", "jfr", "--gradle-version", minimalSupportedGradleVersion, "--gradle-version", "3.0")

        then:
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 7
        logFile.find("<gradle-version: 3.0>").size() == 7
        logFile.find("<tasks: [help]>").size() == 8
        logFile.find("<tasks: [assemble]>").size() == 6

        logFile.containsOne("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 1/4)")
        logFile.containsOne("* Running scenario assemble using Gradle 3.0 (scenario 2/4)")
        logFile.containsOne("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 3/4)")
        logFile.containsOne("* Running scenario help using Gradle 3.0 (scenario 4/4)")

        new File(outputDir, "assemble/$minimalSupportedGradleVersion/assemble-${minimalSupportedGradleVersion}.jfr").file
        new File(outputDir, "assemble/3.0/assemble-3.0.jfr").file
        new File(outputDir, "help/$minimalSupportedGradleVersion/help-${minimalSupportedGradleVersion}.jfr").file
        new File(outputDir, "help/3.0/help-3.0.jfr").file
    }

    def "resolve placeholders in configuration"() {
        given:

        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
default-scenarios = ["assemble", "help"]

baseVersion = "${minimalSupportedGradleVersion}"

defaults = {
    versions = [ \${baseVersion}, "3.0" ]
}

assemble = \${defaults} {
    tasks = assemble
}
help = \${defaults} {
    tasks = help
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--profile", "jfr")

        then:
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 7
        logFile.find("<gradle-version: 3.0>").size() == 7
        logFile.find("<tasks: [assemble]>").size() == 6
        logFile.find("<tasks: [help]>").size() == 8

        logFile.containsOne("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 1/4)")
        logFile.containsOne("* Running scenario assemble using Gradle 3.0 (scenario 2/4)")
        logFile.containsOne("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 3/4)")
        logFile.containsOne("* Running scenario help using Gradle 3.0 (scenario 4/4)")

        new File(outputDir, "help/$minimalSupportedGradleVersion/help-${minimalSupportedGradleVersion}.jfr").file
        new File(outputDir, "help/3.0/help-3.0.jfr").file
    }

    def "runs cleanup tasks defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
help {
    versions = "$minimalSupportedGradleVersion"
    cleanup-tasks = clean
    tasks = help
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 33
        logFile.find("<tasks: [help]>").size() == 17

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,help"
        lines.get(1) == "version,Gradle ${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,help"
        lines.get(3) == "value,execution"
        lines.get(4).matches("warm-up build #1,\\d+")
        lines.get(9).matches("warm-up build #6,\\d+")
        lines.get(10).matches("measured build #1,\\d+")
        lines.get(11).matches("measured build #2,\\d+")
        lines.get(19).matches("measured build #10,\\d+")
    }

    def "runs benchmarks using single scenario defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    versions = ["3.0", "$minimalSupportedGradleVersion"]
    tasks = assemble
}
help {
    versions = "$minimalSupportedGradleVersion"
    tasks = [help]
    run-using = no-daemon
}
"""

        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark", "assemble")

        then:
        logFile.containsOne("* Running scenario assemble using Gradle 3.0 (scenario 1/2)")
        logFile.containsOne("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 2/2)")

        !logFile.find("Tasks: [help]")
    }

    def "dry run runs test builds to verify configuration"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
s1 {
    versions = ["3.0", "$minimalSupportedGradleVersion"]
    tasks = assemble
}
s2 {
    versions = "$minimalSupportedGradleVersion"
    tasks = [clean,assemble]
}
"""

        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<dry-run: " + gradle.startParameter.dryRun + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath, "--benchmark", "--dry-run")

        then:
        // Probe version, 1 warm up, 1 build
        logFile.find("<gradle-version: $minimalSupportedGradleVersion>").size() == 5
        logFile.find("<gradle-version: 3.0").size() == 3
        logFile.find("<dry-run: false>").size() == 2
        logFile.find("<dry-run: true>").size() == 6
        logFile.find("<tasks: [help]>").size() == 2
        logFile.find("<tasks: [assemble]>").size() == 4
        logFile.find("<tasks: [clean, assemble]>").size() == 2

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(2)
        lines.get(0) == "scenario,s1,s1,s2"
        lines.get(1) == "version,Gradle 3.0,Gradle ${minimalSupportedGradleVersion},Gradle ${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble,assemble,clean assemble"
        lines.get(3) == "value,execution,execution,execution"
        lines.get(4).matches("warm-up build #1,\\d+,\\d+,\\d+")
        lines.get(5).matches("measured build #1,\\d+,\\d+,\\d+")
    }

    def "can define system property when benchmarking using tooling API"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<sys-prop: " + System.getProperty("org.gradle.test") + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "-Dorg.gradle.test=value", "assemble")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<sys-prop: null>").size() == 1
        logFile.find("<sys-prop: value>").size() == 16
    }

    def "can define system property in gradle properties"() {
        given:
        file("gradle.properties").text = "org.gradle.jvmargs=-Dorg.gradle.test=value"
        buildFile.text = """
apply plugin: BasePlugin
println "<sys-prop: " + System.getProperty("org.gradle.test") + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", GradleVersion.current().version, "--benchmark", "assemble")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<sys-prop: value>").size() == 17
    }

    def "can define gradle property in gradle properties"() {
        given:
        file("gradle.properties").text = "org.gradle.test=value"
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-prop: " + getProperty("org.gradle.test") + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", GradleVersion.current().version, "--benchmark", "assemble")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<gradle-prop: value>").size() == 17
    }

    def "can define system property when benchmarking using no-daemon"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<sys-prop: " + System.getProperty("org.gradle.test") + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "-Dorg.gradle.test=value", "assemble")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<sys-prop: null>").size() == 1
        logFile.find("<sys-prop: value>").size() == 16
    }

    def "can define system property using scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
a {
    versions = "$minimalSupportedGradleVersion"
    tasks = assemble
    system-properties {
        "org.gradle.test" = "value-1"
    }
}
b {
    versions = "$minimalSupportedGradleVersion"
    tasks = assemble
    system-properties {
        "org.gradle.test" = "value-2"
    }
}
"""
        buildFile.text = """
apply plugin: BasePlugin
println "<sys-prop: " + System.getProperty("org.gradle.test") + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<sys-prop: null>").size() == 1
        logFile.find("<sys-prop: value-1>").size() == 16
        logFile.find("<sys-prop: value-2>").size() == 16
    }

    def "uses default version if none are defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
a {
    tasks = assemble
}
"""
        buildFile.text = """
apply plugin: BasePlugin
println "Running \$gradle.gradleVersion"
"""
        def wrapperProperties = file("gradle/wrapper/gradle-wrapper.properties")
        wrapperProperties.parentFile.mkdirs()
        wrapperProperties.text = "distributionUrl=https\\://services.gradle.org/distributions/gradle-$minimalSupportedGradleVersion-bin.zip"

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark")

        then:
        logFile.containsOne("* Running scenario a using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.find("Running $minimalSupportedGradleVersion")
    }

    def "can define Gradle args using scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
a {
    versions = "$minimalSupportedGradleVersion"
    tasks = assemble
    gradle-args = "-Dorg.gradle.test=value-1"
}
b {
    versions = "$minimalSupportedGradleVersion"
    tasks = assemble
    gradle-args = ["-x", "help", "-Dorg.gradle.test=value-2"]
}
"""
        buildFile.text = """
apply plugin: BasePlugin
println "<sys-prop: " + System.getProperty("org.gradle.test") + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
            "--benchmark")

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<sys-prop: null>").size() == 1
        logFile.find("<sys-prop: value-1>").size() == 16
        logFile.find("<sys-prop: value-2>").size() == 16
    }

    @Unroll
    def "can use Gradle args to #name parallel mode"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
a {
    versions = "$minimalSupportedGradleVersion"
    tasks = assemble
    gradle-args = "$arg"
}
"""
        buildFile.text = """
apply plugin: BasePlugin
println "<parallel: " + gradle.startParameter.parallelProjectExecutionEnabled + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarioFile.absolutePath)

        then:
        // Probe version, 6 warm up, 10 builds
        if (isParallel) {
            logFile.find("<parallel: ${isParallel}>").size() >= 16
            logFile.find("Parallel execution is an incubating feature").size() >= 16
        } else {
            logFile.find("<parallel: ${isParallel}>").size() <= 1
            logFile.find("Parallel execution is an incubating feature").size() <= 1
        }

        where:
        isParallel | arg          | name
        false      | ""           | "disable"
        true       | "--parallel" | "enable"
    }

    def "applies changes to Java source file while running benchmark"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<src-length: \${file('src/main/java/Library.java').length()}>"
"""
        def srcFile = file("src/main/java/Library.java")
        srcFile.parentFile.mkdirs()
        srcFile.text = """
class Library {
    void thing() { }
}
"""
        def originalText = srcFile.text

        def scenarioFile = file("scenarios.conf")
        scenarioFile << """
classes {
    tasks = "help"
    apply-abi-change-to = "src/main/java/Library.java"
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "--scenario-file", scenarioFile.absolutePath)

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<src-length: ${srcFile.length()}>").size() == 1
        logFile.find("<src-length: ${srcFile.length() + (OperatingSystem.windows ? 192 : 183)}>").size() == 6 /* WARM_UP #1..6 */ + 9 /* MEASURE #1..9*/
        logFile.find("<src-length: ${srcFile.length() + (OperatingSystem.windows ? 194 : 185)}>").size() == 1 /* MEASURE #10 */
        srcFile.text == originalText
    }

    def "applies changes to Android resource file while running benchmark"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<src-length: \${file('src/main/res/values/strings.xml').length()}>"
"""
        def srcFile = file("src/main/res/values/strings.xml")
        srcFile.parentFile.mkdirs()
        srcFile.text = """
<resources>
    <string name="app_name">Example</string>
</resources>
"""
        def originalText = srcFile.text

        def scenarioFile = file("scenarios.conf")
        scenarioFile << """
classes {
    tasks = "help"
    apply-android-resource-change-to = "src/main/res/values/strings.xml"
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "--scenario-file", scenarioFile.absolutePath)

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<src-length: ${srcFile.length()}>").size() == 1
        logFile.find("<src-length: ${srcFile.length() + 101}>").size() == 6 /* WARM_UP #1..6 */ + 9 /* MEASURE #1..9*/
        logFile.find("<src-length: ${srcFile.length() + 102}>").size() == 1 /* MEASURE #10 */
        srcFile.text == originalText
    }

    def "applies change to Android resource value while running benchmark"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<src-length: \${file('src/main/res/values/strings.xml').length()}>"
"""
        def srcFile = file("src/main/res/values/strings.xml")
        srcFile.parentFile.mkdirs()
        srcFile.text = """
<resources>
    <string name="app_name">Example</string>
</resources>
"""
        def originalText = srcFile.text

        def scenarioFile = file("scenarios.conf")
        scenarioFile << """
classes {
    tasks = "help"
    apply-android-resource-value-change-to = "src/main/res/values/strings.xml"
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "--scenario-file", scenarioFile.absolutePath)

        then:
        // Probe version, 6 warm up, 10 builds
        logFile.find("<src-length: ${srcFile.length()}>").size() == 1
        logFile.find("<src-length: ${srcFile.length() + 64}>").size() == 6 /* WARM_UP #1..6 */ + 9 /* MEASURE #1..9*/
        logFile.find("<src-length: ${srcFile.length() + 65}>").size() == 1 /* MEASURE #10 */
        srcFile.text == originalText
    }

    def "reverts changes on benchmark failures"() {
        given:
        buildFile.text = """
apply plugin: 'java'
if (file('src/main/java/Library.java').text.contains('_m')) {
    throw new Exception("Boom")
}
"""
        def srcFile = file("src/main/java/Library.java")
        srcFile.parentFile.mkdirs()
        def originalText = """
class Library {
    void thing() { }
}
"""
        srcFile.text = originalText

        def scenarioFile = file("scenarios.conf")
        scenarioFile << """
classes {
    tasks = "classes"
    apply-abi-change-to = "src/main/java/Library.java"
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "--scenario-file", scenarioFile.absolutePath)

        then:
        thrown Exception
        srcFile.text == originalText
    }

    def "uses isolated user home"() {
        given:
        buildFile.text = """
apply plugin: 'base'
println "User home: \$gradle.gradleUserHomeDir"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "help")

        then:
        logFile.find("User home: " + new File("gradle-user-home").absolutePath)
    }

    def "can specify user home"() {
        given:
        buildFile.text = """
apply plugin: 'base'
println "User home: \$gradle.gradleUserHomeDir"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "--gradle-user-home", "home with spaces", "help")

        then:
        logFile.find("User home: " + new File("home with spaces").absolutePath)
    }

    @Requires({ !OperatingSystem.windows })
    def "can benchmark scenario using buck wrapper script"() {
        given:
        writeBuckw()
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    tasks = ["some:assemble"]
    buck {
        targets = "//some/target"
    }
}
buildType {
    tasks = ["assemble"]
    buck {
        type = "android_binary"
    }
}
buildAll {
    tasks = ["assemble"]
    buck {
        type = "all"
    }
}
help {
    tasks = ["help"]
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--buck")

        then:
        logFile.containsOne("* Running scenario buildAll using buck (scenario 1/3)")
        logFile.containsOne("* Buck targets: [//target:android_binary, //target:java_library, //target:cpp_library, //target/child:android_library, //target/child:cpp_library]")
        logFile.containsOne("* Running scenario buildTarget using buck (scenario 2/3)")
        logFile.containsOne("* Buck targets: [//some/target]")
        logFile.containsOne("* Running scenario buildType using buck (scenario 3/3)")
        logFile.containsOne("* Buck targets: [//target:android_binary_1, //target:android_binary_2, //target/child:android_binary_3, //target/child:android_binary_4]")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,buildAll,buildTarget,buildType"
        lines.get(1) == "version,buck,buck,buck"
        lines.get(2) == "tasks,,//some/target,"
        lines.get(3) == "value,execution,execution,execution"
        lines.get(4).matches("warm-up build #1,\\d+,\\d+,\\d+")
        lines.get(9).matches("warm-up build #6,\\d+,\\d+,\\d+")
        lines.get(10).matches("measured build #1,\\d+,\\d+,\\d+")
        lines.get(11).matches("measured build #2,\\d+,\\d+,\\d+")
        lines.get(19).matches("measured build #10,\\d+,\\d+,\\d+")
    }

    def "cannot profile a buck build"() {
        given:
        writeBuckw()
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    buck {
        targets = "//some/target"
    }
}
help {
    tasks = ["help"]
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--buck", "--profile", "jfr", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Can only profile scenario 'buildTarget' when building using Gradle.")
    }

    def "can profile a scenario that contains buck build instructions when building with Gradle"() {
        given:
        writeBuckw()
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    tasks = ["help"]
    buck {
        targets = "//some/target"
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--profile", "jfr", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        logFile.find("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
    }

    def "ignores buck build instructions when benchmarking using Gradle"() {
        given:
        writeBuckw()
        buildFile << "apply plugin: 'base'"
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    tasks = ["help"]
    buck {
        targets = "//some/target"
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        logFile.find("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
    }

    @Requires({ !OperatingSystem.windows && System.getenv("BAZEL_HOME") })
    def "can benchmark scenario using bazel"() {
        given:
        createSimpleBazelWorkspace()
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    tasks = ["some:assemble"]
    bazel {
        targets = ["build", ":hello"]
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--bazel")

        then:
        logFile.containsOne("* Running scenario buildTarget using bazel (scenario 1/1)")
        logFile.containsOne("* Bazel targets: [:hello]")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,buildTarget"
        lines.get(1) == "version,bazel"
        lines.get(2) == "tasks,some:assemble"
        lines.get(3) == "value,execution"
        lines.get(4).matches("warm-up build #1,\\d+")
        lines.get(9).matches("warm-up build #6,\\d+")
        lines.get(10).matches("measured build #1,\\d+")
        lines.get(11).matches("measured build #2,\\d+")
        lines.get(19).matches("measured build #10,\\d+")
    }

    def "cannot profile a bazel build"() {
        given:
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    bazel {
        targets = "//some/target"
    }
}
help {
    tasks = ["help"]
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--bazel", "--profile", "jfr", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Can only profile scenario 'buildTarget' when building using Gradle.")
    }

    def "can profile a scenario that contains bazel build instructions when building with Gradle"() {
        given:
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    tasks = ["help"]
    bazel {
        targets = ["build", "//some/target"]
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--profile", "jfr", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        logFile.find("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
    }

    def "ignores bazel build instructions when benchmarking using Gradle"() {
        given:
        buildFile << "apply plugin: 'base'"
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    tasks = ["help"]
    bazel {
        targets = ["build", "//some/target"]
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        logFile.find("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
    }

    @Requires({ System.getenv("MAVEN_HOME") })
    def "can benchmark scenario using Maven"() {
        given:
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildGoal {
    maven {
        targets = ["-v"]
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--maven")

        then:
        logFile.containsOne("* Running scenario buildGoal using maven (scenario 1/1)")
        logFile.containsOne("* Maven targets: [-v]")

        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,buildGoal"
        lines.get(1) == "version,maven"
        lines.get(2) == "tasks,-v"
        lines.get(3) == "value,execution"
        lines.get(4).matches("warm-up build #1,\\d+")
        lines.get(9).matches("warm-up build #6,\\d+")
        lines.get(10).matches("measured build #1,\\d+")
        lines.get(11).matches("measured build #2,\\d+")
        lines.get(19).matches("measured build #10,\\d+")
    }

    def "clears build cache when asked"() {
        given:
        buildFile << """
            apply plugin: "java"

            def cacheFiles(File gradleHome) {
                file("\${gradleHome}/caches/build-cache-1").listFiles().findAll { it.name.length() == 32 }
            }

            task checkNoCacheBefore {
                doFirst {
                    def files = cacheFiles(gradle.gradleUserHomeDir)
                    assert (files == null || files.empty)
                }
            }
            compileJava.mustRunAfter checkNoCacheBefore

            task checkHasCacheAfter {
                mustRunAfter compileJava
                doFirst {
                    assert !cacheFiles(gradle.gradleUserHomeDir).empty
                }
            }
        """

        file("src/main/java").mkdirs()
        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String... args) {
                    System.out.println("Hello, World!");
                }
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    versions = ["4.5"]
    // Warm daemons don't allow cleaning caches
    daemon = cold
    clear-build-cache-before = BUILD
    gradle-args = ["--build-cache"]
    tasks = ["checkNoCacheBefore", "clean", "compileJava", "checkHasCacheAfter"]
}
"""

        when:
        benchmarkScenario(scenarios)

        then:
        noExceptionThrown()
    }

    def "clears transform cache when asked"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            def usage = Attribute.of('usage', String)
            def artifactType = Attribute.of('artifactType', String)

            repositories {
                mavenCentral()
            }
            dependencies {
                attributesSchema {
                    attribute(usage)
                }

                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                    artifactTransform(FileSizer)
                }

                compile 'com.google.guava:guava:21.0'
            }
            configurations {
                compile {
                    attributes { attribute usage, 'api' }
                }
            }

            class FileSizer extends ArtifactTransform {
                FileSizer() {
                    println "Creating FileSizer"
                }

                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def output = new File(outputDirectory, input.name + ".txt")
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                    return [output]
                }
            }

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
                doLast {
                    println "files: " + artifacts.collect { it.file.name }
                }
            }

            def cacheFiles(File gradleHome) {
                file("\${gradleHome}/caches/transforms-1/files-1.1").listFiles().findAll { it.name.endsWith(".jar") }
            }

            def checkNoCacheBefore() {
                def files = cacheFiles(gradle.gradleUserHomeDir)
                assert (files == null || files.empty)
            }

            gradle.taskGraph.whenReady {
                if (it.hasTask(resolve)) {
                    checkNoCacheBefore()
                }
            }

            task checkHasCacheAfter {
                mustRunAfter resolve
                doFirst {
                    def files = cacheFiles(gradle.gradleUserHomeDir)
                    assert !files.empty
                }
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    versions = ["4.10"]
    // Warm daemons don't allow cleaning caches
    daemon = cold
    clear-transform-cache-before = BUILD
    tasks = ["clean", "resolve", "checkHasCacheAfter"]
}
"""

        when:
        benchmarkScenario(scenarios)

        then:
        noExceptionThrown()
    }

    def "shows build cache size"() {
        given:
        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java").mkdirs()
        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String... args) {
                    System.out.println("Hello, World!");
                }
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    versions = ["4.5"]
    clear-build-cache-before = SCENARIO
    show-build-cache-size = true
    gradle-args = ["--build-cache"]
    cleanup-tasks = ["clean"]
    tasks = ["compileJava"]
}
"""

        when:
        benchmarkScenario(scenarios)

        then:
        output.count("> Build cache size:") == 5
    }

    def "clean project cache when configured (buildSrc: #buildSrc)"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }
        """
        file("src/main/java").mkdirs()
        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String... args) {
                    System.out.println("Hello, World!");
                }
            }
        """
        if (buildSrc) {
            file("buildSrc/src/main/java").mkdirs()
            file("buildSrc/src/main/java/A.java").text = "class A {}"
        }
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    versions = ["${latestSupportedGradleVersion}"]
    // Warm daemons don't allow cleaning caches
    daemon = cold
    clear-project-cache-before = BUILD
    tasks = ["compileJava"]
}
"""

        when:
        benchmarkScenario(scenarios)

        then:
        output.count("> Cleaning project .gradle cache:") == 2
        output.count("> Cleaning buildSrc .gradle cache:") == (buildSrc ? 2 : 0)

        where:
        buildSrc << [false, true]
    }

    def "clean Gradle user home cache when configured"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }
        """
        file("src/main/java").mkdirs()
        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String... args) {
                    System.out.println("Hello, World!");
                }
            }
        """
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    versions = ["${latestSupportedGradleVersion}"]
    clear-gradle-user-home-before = BUILD
    daemon = none
    tasks = ["compileJava"]
}
"""

        when:
        benchmarkScenario(scenarios)

        then:
        output.count("> Cleaning Gradle user home: ") == 2
    }

    def "does Git revert when asked"() {
        given:
        def repoDir = new File(projectDir, "repo")
        def repo = new TestGitRepo(repoDir)

        new File(repoDir, "build.gradle") << """
            task cleanTest {
                doFirst {
                    assert file("file.txt").text == "Final"
                }
            }
            task test {
                doFirst {
                    assert file("file.txt").text == "Original"
                }
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    git-revert = ["${repo.finalCommit}", "${repo.modifiedCommit}"]
    tasks = ["test"]
}
"""

        when:
        benchmarkScenario(repoDir, scenarios)

        then:
        repo.atFinalCommit()
        repo.hasFinalContent()
    }

    def "does Git checkout when asked"() {
        given:
        def repoDir = new File(projectDir, "repo")
        def repo = new TestGitRepo(repoDir)

        new File(repoDir, "build.gradle") << """
            task cleanTest {
                doFirst {
                    assert file("file.txt").text == "Original"
                }
            }
            task test {
                doFirst {
                    assert file("file.txt").text == "Modified"
                }
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    git-checkout = {
        cleanup = ${repo.originalCommit}
        build = ${repo.modifiedCommit}
    }
    cleanup-tasks = ["cleanTest"]
    tasks = ["test"]
}
"""

        when:
        benchmarkScenario(repoDir, scenarios)

        then:
        repo.atFinalCommit()
        repo.hasFinalContent()
    }

    def "does Git checkout when asked with null cleanup"() {
        given:
        def repoDir = new File(projectDir, "repo")
        def repo = new TestGitRepo(repoDir)

        new File(repoDir, "build.gradle") << """
            task cleanTest {
                doFirst {
                    assert file("file.txt").text == "Final"
                }
            }
            task test {
                doFirst {
                    assert file("file.txt").text == "Modified"
                }
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    git-checkout = {
        build = ${repo.modifiedCommit}
    }
    cleanup-tasks = ["cleanTest"]
    tasks = ["test"]
}
"""

        when:
        benchmarkScenario(repoDir, scenarios)

        then:
        repo.atFinalCommit()
        repo.hasFinalContent()
    }

    def "sets system properties with profile parameters"() {
        given:
        buildFile << """
            apply plugin: "java"

            System.getProperties().each { key, value ->
                println "> \$key = \$value"
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
            buildTarget {
                cleanup-tasks = ["clean"]
                tasks = ["compileJava"]
            }
        """

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--scenario-file", scenarios.absolutePath, "--warmups", "2", "--iterations", "2")

        then:
        logFile.find("> org.gradle.profiler.scenario = buildTarget").size() == 8
        logFile.find("> org.gradle.profiler.phase = WARM_UP").size() == 4
        logFile.find("> org.gradle.profiler.phase = MEASURE").size() == 4
        logFile.find("> org.gradle.profiler.step = CLEANUP").size() == 4
        logFile.find("> org.gradle.profiler.step = BUILD").size() == 4
        logFile.find("> org.gradle.profiler.number = 1").size() == 4
        logFile.find("> org.gradle.profiler.number = 2").size() == 4
    }

    def "override jvm args"() {
        given:
        def scenarios = file('performance.scenario')
        scenarios.text = """
            buildTarget {
                tasks = ["jvmArgs"]
                jvm-args = ["-Xmx2G", "-Xms1G"]
            }
        """

        getBuildFile() << """
            import java.lang.management.ManagementFactory

            task jvmArgs {
                doFirst {
                    def jvmArgs = ManagementFactory.runtimeMXBean.inputArguments
                    assert jvmArgs.contains("-Xmx2G")
                    assert jvmArgs.contains("-Xms1G")
                }
            }
        """

        when:
        benchmarkScenario(scenarios)

        then:
        noExceptionThrown()
    }

    private benchmarkScenario(File scenarioFile) {
        benchmarkScenario(projectDir, scenarioFile)
    }

    private benchmarkScenario(File projectDir, File scenarioFile) {
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--benchmark",
            "--scenario-file", scenarioFile.absolutePath,
            "--gradle-version", minimalSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "1",
            "buildTarget"
        )
    }
}
