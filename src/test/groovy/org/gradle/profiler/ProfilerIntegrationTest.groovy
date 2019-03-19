package org.gradle.profiler

import org.gradle.profiler.buildscan.BuildScanProfiler
import org.gradle.profiler.jprofiler.JProfiler
import org.gradle.profiler.yourkit.YourKit
import org.gradle.util.GradleVersion
import spock.lang.Requires
import spock.lang.Unroll

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
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jfr",
                "--warmups", "0", "--iterations", "2", "assemble")

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("You can not skip warm-ups when profiling or benchmarking. Use --no-daemon if you want to profile or benchmark JVM startup")
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
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--benchmark",
                "--iterations", "2", "--scenario-file", scenarioFile.absolutePath)

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("You can not skip warm-ups when profiling or benchmarking scenario help. Use --no-daemon if you want to profile or benchmark JVM startup")
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
        logFile.contains("ERROR: failed to run build. See log file for details.")
        output.contains("ERROR: failed to run build. See log file for details.")
        logFile.contains(e.cause.message)
        output.contains(e.cause.message)
        logFile.contains("java.lang.RuntimeException: broken!")
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
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.contains("* Running scenario using Gradle 3.0 (scenario 2/2)")
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion").size() == 4
        logFile.grep("<gradle-version: 3.0").size() == 4

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
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 3
        logFile.grep("* Running measured build").size() == 2
        logFile.grep("* Starting profiler for daemon with pid").size() == 1
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 6
        logFile.grep("<tasks: [assemble]>").size() == 5

        def profileFile = new File(outputDir, "${minimalSupportedGradleVersion}.jfr")
        profileFile.exists()
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit to produce CPU tracing snapshot"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit",
                "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit to produce CPU tracing snapshot when using no-daemon"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit",
                "--no-daemon", "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit to produce CPU sampling snapshot"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit",
                "--yourkit-sampling", "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit to produce CPU sampling snapshot when using no-daemon"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit",
                "--yourkit-sampling", "--no-daemon", "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit to produce memory snapshot"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit",
                "--yourkit-memory", "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit to produce memory snapshot when using no-daemon"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "yourkit",
                "--yourkit-memory", "--no-daemon", "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}-.+\\.snapshot") }
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
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
        logFile.grep("<gradle-version: $latestSupportedGradleVersion>").size() == 4
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished()
    }

    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles build using JProfiler"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler",
                "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}.jps") }
    }

    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles --no-daemon build using JProfiler"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler",
                "--jprofiler-monitors", "--jprofiler-probes", "builtin.JdbcProbe:+special,builtin.FileProbe,builtin.ClassLoaderProbe:+events",
                "--no-daemon", "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}.jps") }
    }

    @Requires({ new File(JProfiler.getDefaultHomeDir()).exists() })
    def "profiles build using JProfiler with all supported options"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "jprofiler",
                "--jprofiler-config", "instrumentation", "--jprofiler-alloc", "--jprofiler-monitors", "--jprofiler-heapdump",
                "--jprofiler-probes", "builtin.FileProbe,builtin.ClassLoaderProbe:+events",
                "assemble")

        then:
        outputDir.listFiles().find { it.name.matches("${minimalSupportedGradleVersion}.jps") }
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished("1.2")

        def profileFile = new File(outputDir, "${minimalSupportedGradleVersion}.jfr")
        profileFile.isFile()
    }

    @Unroll
    def "profiles build to produce chrome trace output when running #versionUnderTest"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "chrome-trace", "assemble")

        then:
        new File(outputDir, "${versionUnderTest}-trace.html").isFile()

        where:
        versionUnderTest << supportedGradleVersions
    }

    @Unroll
    def "profiles build to produce chrome trace output when running with no daemon and #versionUnderTest"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
            run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "chrome-trace",
                "--no-daemon", "assemble")

        then:
        new File(outputDir, "${versionUnderTest}-trace.html").isFile()

        where:
        versionUnderTest << supportedGradleVersions
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
    run-using = no-daemon
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 1 + 16 * 2
        logFile.grep("<gradle-version: 3.0").size() == 17
        logFile.grep("<daemon: true").size() == 2 + 16 * 2
        logFile.grep("<daemon: false").size() == 16
        logFile.grep("<tasks: [help]>").size() == 2 + 16
        logFile.grep("<tasks: [assemble]>").size() == 16 * 2

        logFile.contains("* Running scenario assemble using Gradle 3.0 (scenario 1/3)")
        logFile.contains("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 2/3)")
        logFile.contains("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 3/3)")

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,assemble,assemble,help"
        lines.get(1) == "version,3.0,${minimalSupportedGradleVersion},${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble,assemble,help"
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.grep("<daemon: true").size() == 17
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: []>").size() == 16

        logFile.contains("* Running scenario xyz using Gradle $minimalSupportedGradleVersion (scenario 1/1)")

        resultFile.isFile()
        resultFile.text.readLines().get(0) == "scenario,xyz"
        resultFile.text.readLines().get(1) == "version,${minimalSupportedGradleVersion}"
        resultFile.text.readLines().get(2) == "tasks,default tasks"
        resultFile.text.readLines().size() == 26 // 3 headers, 16 executions, 7 stats
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 7
        logFile.grep("<tasks: [help]>").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3

        logFile.contains("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.contains("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 2/2)")

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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.grep("<gradle-version: $latestSupportedGradleVersion").size() == 17
        logFile.grep("<daemon: true").size() == 17 * 2
        logFile.grep("<tasks: []>").size() == 16 * 2
        logFile.grep("<idea>").size() == 16 * 2

        logFile.contains("* Running scenario ideaModel using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.contains("* Running scenario ideaModel using Gradle $latestSupportedGradleVersion (scenario 2/2)")

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,ideaModel,ideaModel"
        lines.get(1) == "version,$minimalSupportedGradleVersion,$latestSupportedGradleVersion"
        lines.get(2) == "tasks,model IdeaProject,model IdeaProject"
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.grep("<gradle-version: $latestSupportedGradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 8
        logFile.grep("<tasks: []>").size() == 6
        logFile.grep("<idea>").size() == 6

        logFile.contains("* Running scenario ideaModel using Gradle $minimalSupportedGradleVersion (scenario 1/2)")
        logFile.contains("* Running scenario ideaModel using Gradle $latestSupportedGradleVersion (scenario 2/2)")

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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 7
        logFile.grep("<gradle-version: 3.0>").size() == 7
        logFile.grep("<tasks: [help]>").size() == 8
        logFile.grep("<tasks: [assemble]>").size() == 6

        logFile.contains("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 1/4)")
        logFile.contains("* Running scenario assemble using Gradle 3.0 (scenario 2/4)")
        logFile.contains("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 3/4)")
        logFile.contains("* Running scenario help using Gradle 3.0 (scenario 4/4)")

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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 7
        logFile.grep("<gradle-version: 3.0>").size() == 7
        logFile.grep("<tasks: [assemble]>").size() == 6
        logFile.grep("<tasks: [help]>").size() == 8

        logFile.contains("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 1/4)")
        logFile.contains("* Running scenario assemble using Gradle 3.0 (scenario 2/4)")
        logFile.contains("* Running scenario help using Gradle $minimalSupportedGradleVersion (scenario 3/4)")
        logFile.contains("* Running scenario help using Gradle 3.0 (scenario 4/4)")

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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 33
        logFile.grep("<tasks: [help]>").size() == 17

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,help"
        lines.get(1) == "version,${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,help"
        lines.get(3).matches("warm-up build #1,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+")
        lines.get(9).matches("measured build #1,\\d+")
        lines.get(10).matches("measured build #2,\\d+")
        lines.get(18).matches("measured build #10,\\d+")
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
        logFile.contains("* Running scenario assemble using Gradle 3.0 (scenario 1/2)")
        logFile.contains("* Running scenario assemble using Gradle $minimalSupportedGradleVersion (scenario 2/2)")

        !logFile.grep("Tasks: [help]")
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
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 5
        logFile.grep("<gradle-version: 3.0").size() == 3
        logFile.grep("<dry-run: false>").size() == 2
        logFile.grep("<dry-run: true>").size() == 6
        logFile.grep("<tasks: [help]>").size() == 2
        logFile.grep("<tasks: [assemble]>").size() == 4
        logFile.grep("<tasks: [clean, assemble]>").size() == 2

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 12
        lines.get(0) == "scenario,s1,s1,s2"
        lines.get(1) == "version,3.0,${minimalSupportedGradleVersion},${minimalSupportedGradleVersion}"
        lines.get(2) == "tasks,assemble,assemble,clean assemble"
        lines.get(3).matches("warm-up build #1,\\d+,\\d+,\\d+")
        lines.get(4).matches("measured build #1,\\d+,\\d+,\\d+")
    }

    def "recovers from failure in warmup while running benchmarks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"

class Holder {
    static int counter
}

assemble.doFirst {
    if (gradle.gradleVersion == "${minimalSupportedGradleVersion}" && Holder.counter++ > 2) {
        throw new RuntimeException("broken!")
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--gradle-version", "3.0", "--benchmark", "assemble")

        then:
        def e = thrown(Main.ScenarioFailedException)
        logFile.contains(e.cause.message)
        output.contains(e.cause.message)
        logFile.contains("java.lang.RuntimeException: broken!")
        output.contains("java.lang.RuntimeException: broken!")

        // Probe version, 5 warm up, 10 builds
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 5
        logFile.grep("<gradle-version: 3.0>").size() == 17
        logFile.grep("<tasks: [help]>").size() == 2
        logFile.grep("<tasks: [assemble]>").size() == 4 + 16

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion},3.0"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3).matches("warm-up build #1,\\d+,\\d+")
        lines.get(4).matches("warm-up build #2,\\d+,\\d+")
        lines.get(5).matches("warm-up build #3,\\d+,\\d+")
        lines.get(6).matches("warm-up build #4,,\\d+")
        lines.get(7).matches("warm-up build #5,,\\d+")
        lines.get(8).matches("warm-up build #6,,\\d+")
        lines.get(9).matches("measured build #1,,\\d+")
        lines.get(10).matches("measured build #2,,\\d+")
        lines.get(18).matches("measured build #10,,\\d+")
        lines.get(19).matches("mean,NaN,\\d+\\.\\d+")
        lines.get(22).matches("median,NaN,\\d+\\.\\d+")
        lines.get(25).matches("stddev,NaN,\\d+\\.\\d+")
    }

    def "recovers from failure running benchmarks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"

class Holder {
    static int counter
}

assemble.doFirst {
    if (gradle.gradleVersion == "${minimalSupportedGradleVersion}" && Holder.counter++ > 7) {
        throw new RuntimeException("broken!")
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--gradle-version", "3.0", "--benchmark", "assemble")

        then:
        def e = thrown(Main.ScenarioFailedException)
        logFile.contains(e.cause.message)
        output.contains(e.cause.message)
        logFile.contains("java.lang.RuntimeException: broken!")
        output.contains("java.lang.RuntimeException: broken!")

        // Probe version, 6 warm up, 10 builds
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 10
        logFile.grep("<gradle-version: 3.0>").size() == 17
        logFile.grep("<tasks: [help]>").size() == 2
        logFile.grep("<tasks: [assemble]>").size() == 9 + 16

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,${minimalSupportedGradleVersion},3.0"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3).matches("warm-up build #1,\\d+,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+,\\d+")
        lines.get(9).matches("measured build #1,\\d+,\\d+")
        lines.get(10).matches("measured build #2,\\d+,\\d+")
        lines.get(11).matches("measured build #3,,\\d+")
        lines.get(18).matches("measured build #10,,\\d+")
        lines.get(19).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(22).matches("median,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(25).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+")
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
        logFile.grep("<sys-prop: null>").size() == 1
        logFile.grep("<sys-prop: value>").size() == 16
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
        logFile.grep("<sys-prop: value>").size() == 17
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
        logFile.grep("<gradle-prop: value>").size() == 17
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
        logFile.grep("<sys-prop: null>").size() == 1
        logFile.grep("<sys-prop: value>").size() == 16
    }

    def "can define system property using scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
a {
    versions = "$minimalSupportedGradleVersion"
    tasks = assemble
    system-properties {
        org.gradle.test = "value-1"
    }
}
b {
    versions = "$minimalSupportedGradleVersion"
    tasks = assemble
    system-properties {
        org.gradle.test = "value-2"
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
        logFile.grep("<sys-prop: null>").size() == 1
        logFile.grep("<sys-prop: value-1>").size() == 16
        logFile.grep("<sys-prop: value-2>").size() == 16
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
        logFile.contains("* Running scenario a using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("Running $minimalSupportedGradleVersion")
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
        logFile.grep("<sys-prop: null>").size() == 1
        logFile.grep("<sys-prop: value-1>").size() == 16
        logFile.grep("<sys-prop: value-2>").size() == 16
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
            logFile.grep("<parallel: ${isParallel}>").size() >= 16
            logFile.grep("Parallel execution is an incubating feature").size() >= 16
        } else {
            logFile.grep("<parallel: ${isParallel}>").size() <= 1
            logFile.grep("Parallel execution is an incubating feature").size() <= 1
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
        logFile.grep("<src-length: ${srcFile.length()}>").size() == 1
        logFile.grep("<src-length: ${srcFile.length() + (OperatingSystem.windows ? 96 : 87)}>").size() == 9
        logFile.grep("<src-length: ${srcFile.length() + (OperatingSystem.windows ? 98 : 89)}>").size() == 7
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
        logFile.grep("<src-length: ${srcFile.length()}>").size() == 1
        logFile.grep("<src-length: ${srcFile.length() + 53}>").size() == 9
        logFile.grep("<src-length: ${srcFile.length() + 54}>").size() == 7
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
        logFile.grep("<src-length: ${srcFile.length()}>").size() == 1
        logFile.grep("<src-length: ${srcFile.length() + 16}>").size() == 9
        logFile.grep("<src-length: ${srcFile.length() + 17}>").size() == 7
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
        logFile.grep("User home: " + new File("gradle-user-home").absolutePath)
    }

    def "Can specify user home"() {
        given:
        buildFile.text = """
apply plugin: 'base'
println "User home: \$gradle.gradleUserHomeDir"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
            "--benchmark", "--gradle-user-home", "foobar", "help")

        then:
        logFile.grep("User home: " + new File("foobar").absolutePath)
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
        logFile.contains("* Running scenario buildAll using buck (scenario 1/3)")
        logFile.contains("* Buck targets: [//target:android_binary, //target:java_library, //target:cpp_library, //target/child:android_library, //target/child:cpp_library]")
        logFile.contains("* Running scenario buildTarget using buck (scenario 2/3)")
        logFile.contains("* Buck targets: [//some/target]")
        logFile.contains("* Running scenario buildType using buck (scenario 3/3)")
        logFile.contains("* Buck targets: [//target:android_binary_1, //target:android_binary_2, //target/child:android_binary_3, //target/child:android_binary_4]")

        resultFile.isFile()
        List<String> lines = resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,buildAll,buildTarget,buildType"
        lines.get(1) == "version,buck,buck,buck"
        lines.get(2) == "tasks,,//some/target,"
        lines.get(3).matches("warm-up build #1,\\d+,\\d+,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+,\\d+,\\d+")
        lines.get(9).matches("measured build #1,\\d+,\\d+,\\d+")
        lines.get(10).matches("measured build #2,\\d+,\\d+,\\d+")
        lines.get(18).matches("measured build #10,\\d+,\\d+,\\d+")
        lines.get(19).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(22).matches("median,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
        lines.get(25).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
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
        logFile.grep("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
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
        logFile.grep("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
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
        targets = [":hello"]
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--bazel")

        then:
        logFile.contains("* Running scenario buildTarget using bazel (scenario 1/1)")
        logFile.contains("* Bazel targets: [:hello]")

        resultFile.isFile()
        List<String> lines =resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,buildTarget"
        lines.get(1) == "version,bazel"
        lines.get(2) == "tasks,some:assemble"
        lines.get(3).matches("warm-up build #1,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+")
        lines.get(9).matches("measured build #1,\\d+")
        lines.get(10).matches("measured build #2,\\d+")
        lines.get(18).matches("measured build #10,\\d+")
        lines.get(19).matches("mean,\\d+\\.\\d+")
        lines.get(22).matches("median,\\d+\\.\\d+")
        lines.get(25).matches("stddev,\\d+\\.\\d+")
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
        targets = "//some/target"
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--profile", "jfr", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        logFile.grep("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
    }

    def "ignores bazel build instructions when benchmarking using Gradle"() {
        given:
        buildFile << "apply plugin: 'base'"
        def scenarios = file("performance.scenario")
        scenarios.text = """
buildTarget {
    tasks = ["help"]
    bazel {
        targets = "//some/target"
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget")

        then:
        logFile.grep("* Running scenario buildTarget using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
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
        logFile.contains("* Running scenario buildGoal using maven (scenario 1/1)")
        logFile.contains("* Maven targets: [-v]")

        resultFile.isFile()
        List<String> lines =resultFile.text.readLines()
        lines.size() == 26 // 3 headers, 16 executions, 7 stats
        lines.get(0) == "scenario,buildGoal"
        lines.get(1) == "version,maven"
        lines.get(2) == "tasks,-v"
        lines.get(3).matches("warm-up build #1,\\d+")
        lines.get(8).matches("warm-up build #6,\\d+")
        lines.get(9).matches("measured build #1,\\d+")
        lines.get(10).matches("measured build #2,\\d+")
        lines.get(18).matches("measured build #10,\\d+")
        lines.get(19).matches("mean,\\d+\\.\\d+")
        lines.get(22).matches("median,\\d+\\.\\d+")
        lines.get(25).matches("stddev,\\d+\\.\\d+")
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
    clear-build-cache-before = BUILD
    gradle-args = ["--build-cache"]
    tasks = ["checkNoCacheBefore", "clean", "compileJava", "checkHasCacheAfter"]
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "buildTarget", "--warmups", "1", "--iterations", "1")

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
    clear-transform-cache-before = BUILD
    tasks = ["clean", "resolve", "checkHasCacheAfter"]
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "buildTarget", "--warmups", "1", "--iterations", "1")

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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget", "--warmups", "1", "--iterations", "1")

        then:
        output.count("> Build cache size:") == 5
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
        new Main().run("--project-dir", repoDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget", "--warmups", "1", "--iterations", "1")

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
        new Main().run("--project-dir", repoDir.absolutePath, "--output-dir", outputDir.absolutePath, "--benchmark", "--scenario-file", scenarios.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "buildTarget", "--warmups", "1", "--iterations", "1")

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
        logFile.grep("> org.gradle.profiler.scenario = buildTarget").size() == 8
        logFile.grep("> org.gradle.profiler.phase = WARM_UP").size() == 4
        logFile.grep("> org.gradle.profiler.phase = MEASURE").size() == 4
        logFile.grep("> org.gradle.profiler.step = CLEANUP").size() == 4
        logFile.grep("> org.gradle.profiler.step = BUILD").size() == 4
        logFile.grep("> org.gradle.profiler.number = 1").size() == 4
        logFile.grep("> org.gradle.profiler.number = 2").size() == 4
    }
}
