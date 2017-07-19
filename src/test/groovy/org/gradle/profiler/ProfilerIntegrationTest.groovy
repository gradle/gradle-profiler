package org.gradle.profiler

import org.gradle.profiler.bs.BuildScanProfiler
import org.gradle.profiler.jprofiler.JProfiler
import org.gradle.profiler.yjp.YourKit
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ProfilerIntegrationTest extends Specification {

    @Shared
    List<String> supportedGradleVersions = ["3.3", "3.4.1", "3.5", "4.0-milestone-1", "4.0-milestone-2", "4.0-rc-1"]
    @Shared
    String minimalSupportedGradleVersion = supportedGradleVersions.first()
    @Shared
    String latestSupportedGradleVersion = supportedGradleVersions.last()

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()
    ByteArrayOutputStream outputBuffer

    File projectDir
    File outputDir

    String getOutput() {
        System.out.flush()
        return new String(outputBuffer.toByteArray())
    }

    LogFile getLogFile() {
        def f = new File(outputDir, "profile.log")
        assert f.isFile()
        return new LogFile(f)
    }

    File getResultFile() {
        new File(outputDir, "benchmark.csv")
    }

    File getBuildFile() {
        return new File(projectDir, "build.gradle")
    }

    File file(String path) {
        return new File(projectDir, path)
    }

    def setup() {
        Logging.resetLogging()
        outputBuffer = new ByteArrayOutputStream()
        System.out = new PrintStream(new TeeOutputStream(System.out, outputBuffer))
        projectDir = tmpDir.newFolder()
        outputDir = tmpDir.newFolder()
    }

    def cleanup() {
        Logging.resetLogging()
    }

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

    def "profiles build using JFR, specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", versionUnderTest, "--profile", "jfr",
                        "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.contains("* Running scenario using Gradle $versionUnderTest (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 2
        logFile.grep("* Running build").size() == 1
        logFile.grep("* Starting recording for daemon with pid").size() == 1
        logFile.grep("<gradle-version: $versionUnderTest>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3

        def profileFile = new File(outputDir, "${versionUnderTest}.jfr")
        profileFile.exists()

        where:
        versionUnderTest              | _
        minimalSupportedGradleVersion | _
        latestSupportedGradleVersion  | _
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
        logFile.grep("* Running build").size() == 2
        logFile.grep("* Starting recording for daemon with pid").size() == 2
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 6
        logFile.grep("<tasks: [assemble]>").size() == 5

        def profileFile = new File(outputDir, "${minimalSupportedGradleVersion}.jfr")
        profileFile.exists()
    }

    @Requires({
        System.getenv('HP_HOME_DIR')
    })
    def "profiles build using Honest Profiler, specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion, "--profile", "hp",
                        "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3

        def profileFile = new File(outputDir, "${minimalSupportedGradleVersion}.hpl")
        profileFile.exists() && profileFile.size()>0
        def profileTxtFile = new File(outputDir, "$minimalSupportedGradleVersion-hp.txt")
        profileTxtFile.exists() && profileTxtFile.size()>0
        def sanitizedProfileTxtFile = new File(outputDir, "$minimalSupportedGradleVersion-hp-sanitized.txt")
        sanitizedProfileTxtFile.exists()  && sanitizedProfileTxtFile.size()>0

        if (System.getenv('FG_HOME_DIR')) {
            def fgFile = new File(outputDir, "${minimalSupportedGradleVersion}-hp-flames.svg")
            assert fgFile.exists() && fgFile.size()>0
        }
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
        assertBuildScanPublished(BuildScanProfiler.VERSION)
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

    private void assertBuildScanPublished(String buildScanPluginVersion) {
        assert logFile.grep("Using build scan profiler version " + buildScanPluginVersion).size() == 1
        // Must be 1, may be 2 if user applies build scan in home dir
        assert logFile.grep("Publishing build").size() >= 1 : ("LOG FILE:" + logFile.text)
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
                        "--profile", "buildscan",  "--buildscan-version", "1.2",
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

    def "runs benchmarks using tooling API for specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
                "--benchmark", "assemble")

        then:
        // Probe version, initial clean build, 6 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 6
        logFile.grep("* Running build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.grep("<daemon: true").size() == 17
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 16

        resultFile.isFile()
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
        resultFile.text.readLines().get(0) == "build,${minimalSupportedGradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+")
        resultFile.text.readLines().get(7).matches("warm-up build 6,\\d+")
        resultFile.text.readLines().get(8).matches("build 1,\\d+")
        resultFile.text.readLines().get(9).matches("build 2,\\d+")
        resultFile.text.readLines().get(17).matches("build 10,\\d+")
        resultFile.text.readLines().get(18).matches("mean,\\d+\\.\\d+")
        resultFile.text.readLines().get(19).matches("median,\\d+\\.\\d+")
        resultFile.text.readLines().get(20).matches("stddev,\\d+\\.\\d+")
    }

    def "runs benchmarks using CLI for specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
                "--benchmark", "--cli", "assemble")

        then:
        // Probe version, initial clean build, 6 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 6
        logFile.grep("* Running build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 17
        logFile.grep("<daemon: true").size() == 17
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 16

        resultFile.isFile()
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
        resultFile.text.readLines().get(0) == "build,${minimalSupportedGradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+")
        resultFile.text.readLines().get(7).matches("warm-up build 6,\\d+")
        resultFile.text.readLines().get(8).matches("build 1,\\d+")
        resultFile.text.readLines().get(9).matches("build 2,\\d+")
        resultFile.text.readLines().get(17).matches("build 10,\\d+")
        resultFile.text.readLines().get(18).matches("mean,\\d+\\.\\d+")
        resultFile.text.readLines().get(19).matches("median,\\d+\\.\\d+")
        resultFile.text.readLines().get(20).matches("stddev,\\d+\\.\\d+")
    }

    def "runs benchmarks using no-daemon for specified Gradle version and tasks"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", minimalSupportedGradleVersion,
                "--benchmark", "--no-daemon", "assemble")

        then:
        // Probe version, 1 warm up, 10 builds
        logFile.contains("* Running scenario using Gradle $minimalSupportedGradleVersion (scenario 1/1)")
        logFile.grep("* Running warm-up build").size() == 1
        logFile.grep("* Running build").size() == 10
        logFile.grep("<gradle-version: $minimalSupportedGradleVersion>").size() == 12
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 11
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 11

        resultFile.isFile()
        resultFile.text.readLines().get(0) == "build,${minimalSupportedGradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble"
        resultFile.text.readLines().size() == 16 // 2 headers, 11 executions, 3 stats
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
        resultFile.text.readLines().get(0) == "build,assemble 3.0,assemble ${minimalSupportedGradleVersion},help ${minimalSupportedGradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble,assemble,help"
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
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
        resultFile.text.readLines().get(0) == "build,help ${minimalSupportedGradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,help"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+")
        resultFile.text.readLines().get(7).matches("warm-up build 6,\\d+")
        resultFile.text.readLines().get(8).matches("build 1,\\d+")
        resultFile.text.readLines().get(9).matches("build 2,\\d+")
        resultFile.text.readLines().get(17).matches("build 10,\\d+")
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
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
        resultFile.text.readLines().get(0) == "build,s1 3.0,s1 ${minimalSupportedGradleVersion},s2 ${minimalSupportedGradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble,assemble,clean assemble"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(3).matches("build 1,\\d+,\\d+,\\d+")
        resultFile.text.readLines().size() == 7
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
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
        resultFile.text.readLines().get(0) == "build,${minimalSupportedGradleVersion},3.0"
        resultFile.text.readLines().get(1) == "tasks,assemble,assemble"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+,\\d+")
        resultFile.text.readLines().get(3).matches("warm-up build 2,\\d+,\\d+")
        resultFile.text.readLines().get(4).matches("warm-up build 3,\\d+,\\d+")
        resultFile.text.readLines().get(5).matches("warm-up build 4,,\\d+")
        resultFile.text.readLines().get(6).matches("warm-up build 5,,\\d+")
        resultFile.text.readLines().get(7).matches("warm-up build 6,,\\d+")
        resultFile.text.readLines().get(8).matches("build 1,,\\d+")
        resultFile.text.readLines().get(9).matches("build 2,,\\d+")
        resultFile.text.readLines().get(17).matches("build 10,,\\d+")
        resultFile.text.readLines().get(18).matches("mean,NaN,\\d+\\.\\d+")
        resultFile.text.readLines().get(19).matches("median,NaN,\\d+\\.\\d+")
        resultFile.text.readLines().get(20).matches("stddev,NaN,\\d+\\.\\d+")
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
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
        resultFile.text.readLines().get(0) == "build,${minimalSupportedGradleVersion},3.0"
        resultFile.text.readLines().get(1) == "tasks,assemble,assemble"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+,\\d+")
        resultFile.text.readLines().get(7).matches("warm-up build 6,\\d+,\\d+")
        resultFile.text.readLines().get(8).matches("build 1,\\d+,\\d+")
        resultFile.text.readLines().get(9).matches("build 2,\\d+,\\d+")
        resultFile.text.readLines().get(10).matches("build 3,,\\d+")
        resultFile.text.readLines().get(17).matches("build 10,,\\d+")
        resultFile.text.readLines().get(18).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+")
        resultFile.text.readLines().get(19).matches("median,\\d+\\.\\d+,\\d+\\.\\d+")
        resultFile.text.readLines().get(20).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+")
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
        logFile.grep("<src-length: ${srcFile.length() + (OperatingSystem.windows ? 89 : 80)}>").size() == 9
        logFile.grep("<src-length: ${srcFile.length() + (OperatingSystem.windows ? 91 : 82)}>").size() == 7
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

    @Requires({!OperatingSystem.windows})
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
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
        resultFile.text.readLines().get(0) == "build,buildAll buck,buildTarget buck,buildType buck"
        resultFile.text.readLines().get(1) == "tasks,,,"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(7).matches("warm-up build 6,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(8).matches("build 1,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(9).matches("build 2,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(17).matches("build 10,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(18).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
        resultFile.text.readLines().get(19).matches("median,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
        resultFile.text.readLines().get(20).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
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

    @Requires({System.getenv("MAVEN_HOME")})
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
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
        resultFile.text.readLines().get(0) == "build,buildGoal maven"
        resultFile.text.readLines().get(1) == "tasks,"
        resultFile.text.readLines().get(2).matches("warm-up build 1,\\d+")
        resultFile.text.readLines().get(7).matches("warm-up build 6,\\d+")
        resultFile.text.readLines().get(8).matches("build 1,\\d+")
        resultFile.text.readLines().get(9).matches("build 2,\\d+")
        resultFile.text.readLines().get(17).matches("build 10,\\d+")
        resultFile.text.readLines().get(18).matches("mean,\\d+\\.\\d+")
        resultFile.text.readLines().get(19).matches("median,\\d+\\.\\d+")
        resultFile.text.readLines().get(20).matches("stddev,\\d+\\.\\d+")
    }

    def writeBuckw() {
        def buckw = file("buckw")
        buckw.text = '''
#!/usr/bin/env bash

echo "[-] PARSING BUCK FILES...FINISHED 0.3s [100%]"
if [ $1 = "targets" ]
then
    if [ "$2" = "--type" ]
    then
        echo "//target:$3_1"
        echo "//target:$3_2"
        echo "//target/child:$3_3"
        echo "//target/child:$3_4"
    else
        echo "//target:android_binary"
        echo "//target:java_library"
        echo "//target:cpp_library"
        echo "//target/child:android_library"
        echo "//target/child:cpp_library"
    fi
else
    echo "building $@"
fi
'''
        buckw.executable = true
    }

    static class LogFile {
        final List<String> lines

        LogFile(File logFile) {
            lines = logFile.readLines()
        }

        @Override
        String toString() {
            return lines.join("\n")
        }

        boolean contains(String str) {
            return grep(str).size() == 1
        }

        /**
         * Locates the lines containing the given string
         */
        List<String> grep(String str) {
            lines.findAll { it.contains(str) }
        }

        String getText() {
            lines.join("\n")
        }
    }
}
