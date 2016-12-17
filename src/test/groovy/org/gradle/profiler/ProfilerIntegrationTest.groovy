package org.gradle.profiler

import org.gradle.profiler.bs.BuildScanInitScript
import org.gradle.profiler.yjp.YourKit
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ProfilerIntegrationTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()
    ByteArrayOutputStream outputBuffer
    @Shared
    String gradleVersion = "3.2.1"
    @Shared
    String gradleNightlyVersion = "3.3-20161205000012+0000"
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

    def "complains when no project directory provided"() {
        when:
        new Main().run()

        then:
        thrown(CommandLineParser.SettingsNotAvailableException)

        and:
        output.contains("No project directory specified.")
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
        output.contains("Unrecognized key 'assemble.gradle-version' found in scenario file " + scenarioFile)
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
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--profile", "jfr",
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

    def "profiles build using specified Gradle version and tasks"() {
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
        logFile.grep("<gradle-version: $versionUnderTest>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3

        def profileFile = new File(outputDir, "default/profile.jfr")
        profileFile.exists()

        where:
        versionUnderTest     | _
        gradleVersion        | _
        gradleNightlyVersion | _
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
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--profile", "hp",
                        "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.grep("<gradle-version: $gradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3

        def profileFile = new File(outputDir, "profile.hpl")
        profileFile.exists() && profileFile.size()>0
        def profileTxtFile = new File(outputDir, "profile.txt")
        profileTxtFile.exists() && profileTxtFile.size()>0
        def sanitizedProfileTxtFile = new File(outputDir, "profile-sanitized.txt")
        sanitizedProfileTxtFile.exists()  && sanitizedProfileTxtFile.size()>0

        if (System.getenv('FG_HOME_DIR')) {
            def fgFile = new File(outputDir, "default/flames.svg")
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
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--profile", "yourkit",
                        "assemble")

        then:
        def sessionDir = new File(outputDir, "default")
        sessionDir.listFiles().find { it.name.matches("default-.+\\.snapshot") }
    }

    @Requires({ YourKit.findYourKitHome() })
    def "profiles build using YourKit to produce memory snapshot"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
"""

        when:
        new Main().
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--profile", "yourkit",
                        "--yourkit-memory", "assemble")

        then:
        def sessionDir = new File(outputDir, "default")
        sessionDir.listFiles().find { it.name.matches("default-.+\\.snapshot") }
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
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion, "--profile", "buildscan",
                        "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.grep("<gradle-version: $gradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished(BuildScanInitScript.VERSION)
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
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                        "--profile", "buildscan", "--buildscan-version", "1.2",
                        "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.grep("<gradle-version: $gradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished("1.2")
    }

    private void assertBuildScanPublished(String buildScanPluginVersion) {
        assert logFile.grep("Using build scan profiler version " + buildScanPluginVersion).size() == 1
        // Must be 1, may be 2 if user applies build scan in home dir
        assert logFile.grep("Publishing build information...").size() >= 1 : ("LOG FILE:" + logFile.text)
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
                run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                        "--profile", "buildscan",  "--buildscan-version", "1.2",
                        "--profile", "jfr",
                        "assemble")

        then:
        // Probe version, 2 warm up, 1 build
        logFile.grep("<gradle-version: $gradleVersion>").size() == 4
        logFile.grep("<daemon: true").size() == 4
        logFile.grep("<tasks: [assemble]>").size() == 3
        assertBuildScanPublished("1.2")

        def profileFile = new File(outputDir, "default/profile.jfr")
        profileFile.exists()
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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--benchmark", "assemble")

        then:
        // Probe version, initial clean build, 2 warm up, 13 builds
        logFile.grep("<gradle-version: $gradleVersion>").size() == 17
        logFile.grep("<daemon: true").size() == 17
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [clean, assemble]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 15

        resultFile.isFile()
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
        resultFile.text.readLines().get(0) == "build,default ${gradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble"
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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--benchmark", "--no-daemon", "assemble")

        then:
        // Probe version, initial clean build, 2 warm up, 13 builds
        logFile.grep("<gradle-version: $gradleVersion>").size() == 17
        logFile.grep("<daemon: true").size() == 1
        logFile.grep("<daemon: false").size() == 16
        logFile.grep("<tasks: [help]>").size() == 1
        logFile.grep("<tasks: [clean, assemble]>").size() == 1
        logFile.grep("<tasks: [assemble]>").size() == 15

        resultFile.isFile()
        resultFile.text.readLines().get(0) == "build,default ${gradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble"
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
    }

    def "runs benchmarks using scenarios defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    versions = ["3.0", "$gradleVersion"]
    tasks = assemble
}
help {
    versions = "$gradleVersion"
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
        // Probe version, initial clean build, 2 warm up, 13 builds
        logFile.grep("<gradle-version: $gradleVersion>").size() == 1 + 16 * 2
        logFile.grep("<gradle-version: 3.0").size() == 17
        logFile.grep("<daemon: true").size() == 2 + 16 * 2
        logFile.grep("<daemon: false").size() == 16
        logFile.grep("<tasks: [help]>").size() == 2 + 15
        logFile.grep("<tasks: [clean, help]>").size() == 1
        logFile.grep("<tasks: [clean, assemble]>").size() == 2
        logFile.grep("<tasks: [assemble]>").size() == 15 * 2

        resultFile.isFile()
        resultFile.text.readLines().get(0) == "build,assemble 3.0,assemble ${gradleVersion},help ${gradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble,assemble,help"
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
    }

    def "runs cleanup tasks defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
help {
    versions = "$gradleVersion"
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
        // Probe version, initial clean build, 2 warm up, 13 cleans, 13 builds
        logFile.grep("<gradle-version: $gradleVersion>").size() == 30
        logFile.grep("<tasks: [clean, help]>").size() == 1
        logFile.grep("<tasks: [help]>").size() == 16

        resultFile.isFile()
        resultFile.text.readLines().get(0) == "build,help ${gradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,help"
        resultFile.text.readLines().size() == 31
    }

    def "runs benchmarks using single scenario defined in scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
assemble {
    versions = ["3.0", "$gradleVersion"]
    tasks = assemble
}
help {
    versions = "$gradleVersion"
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
        !logFile.grep("Tasks: [help]")
    }

    def "dry run runs test builds to verify configuration"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
s1 {
    versions = ["3.0", "$gradleVersion"]
    tasks = assemble
}
s2 {
    versions = "$gradleVersion"
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
        // Probe version, initial clean build, 1 warm up, 1 build
        logFile.grep("<gradle-version: $gradleVersion>").size() == 7
        logFile.grep("<gradle-version: 3.0").size() == 4
        logFile.grep("<dry-run: false>").size() == 2
        logFile.grep("<dry-run: true>").size() == 9
        logFile.grep("<tasks: [help]>").size() == 2
        logFile.grep("<tasks: [assemble]>").size() == 4
        logFile.grep("<tasks: [clean, assemble]>").size() == 5

        resultFile.isFile()
        resultFile.text.readLines().get(0) == "build,s1 3.0,s1 ${gradleVersion},s2 ${gradleVersion}"
        resultFile.text.readLines().get(1) == "tasks,assemble,assemble,clean assemble"
        resultFile.text.readLines().get(2).matches("initial clean build,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(3).matches("warm-up build 1,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(4).matches("build 1,\\d+,\\d+,\\d+")
        resultFile.text.readLines().get(5).matches("mean,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
        resultFile.text.readLines().get(6).matches("median,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
        resultFile.text.readLines().get(7).matches("stddev,\\d+\\.\\d+,\\d+\\.\\d+,\\d+\\.\\d+")
        resultFile.text.readLines().size() == 8
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
    if (gradle.gradleVersion == "${gradleVersion}" && Holder.counter++ > 3) {
        throw new RuntimeException("broken!")
    }
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--gradle-version", "3.0", "--benchmark", "assemble")

        then:
        def e = thrown(Main.ScenarioFailedException)
        logFile.contains(e.cause.message)
        output.contains(e.cause.message)
        logFile.contains("java.lang.RuntimeException: broken!")
        output.contains("java.lang.RuntimeException: broken!")

        // Probe version, initial clean build, 2 warm up, 13 builds
        logFile.grep("<gradle-version: $gradleVersion>").size() == 7
        logFile.grep("<gradle-version: 3.0>").size() == 17
        logFile.grep("<tasks: [help]>").size() == 2
        logFile.grep("<tasks: [clean, assemble]>").size() == 2
        logFile.grep("<tasks: [assemble]>").size() == 5 + 15

        resultFile.isFile()
        resultFile.text.readLines().get(0) == "build,default ${gradleVersion},default 3.0"
        resultFile.text.readLines().get(1) == "tasks,assemble,assemble"
        resultFile.text.readLines().get(2).matches("initial clean build,\\d+,\\d+")
        resultFile.text.readLines().get(3).matches("warm-up build 1,\\d+,\\d+")
        resultFile.text.readLines().get(4).matches("warm-up build 2,\\d+,\\d+")
        resultFile.text.readLines().get(5).matches("build 1,\\d+,\\d+")
        resultFile.text.readLines().get(6).matches("build 2,\\d+,\\d+")
        resultFile.text.readLines().get(7).matches("build 3,,\\d+")
        resultFile.text.readLines().size() == 21 // 2 headers, 16 executions, 3 stats
    }

    def "can define system property when benchmarking using tooling API"() {
        given:
        buildFile.text = """
apply plugin: BasePlugin
println "<sys-prop: " + System.getProperty("org.gradle.test") + ">"
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--benchmark", "-Dorg.gradle.test=value", "assemble")

        then:
        // Probe version, initial clean build, 2 warm up, 13 builds
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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--benchmark", "-Dorg.gradle.test=value", "assemble")

        then:
        // Probe version, initial clean build, 2 warm up, 13 builds
        logFile.grep("<sys-prop: null>").size() == 1
        logFile.grep("<sys-prop: value>").size() == 16
    }

    def "can define system property using scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
a {
    versions = "$gradleVersion"
    tasks = assemble
    system-properties {
        org.gradle.test = "value-1"
    }
}
b {
    versions = "$gradleVersion"
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
        // Probe version, initial clean build, 2 warm up, 13 builds
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
        wrapperProperties.text = "distributionUrl=https\\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--scenario-file", scenarioFile.absolutePath,
                "--benchmark")

        then:
        logFile.grep("Running $gradleVersion")
    }

    def "can define Gradle args using scenario file"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
a {
    versions = "$gradleVersion"
    tasks = assemble
    gradle-args = "-Dorg.gradle.test=value-1"
}
b {
    versions = "$gradleVersion"
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
        // Probe version, initial clean build, 2 warm up, 13 builds
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
    versions = "$gradleVersion"
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
        // Probe version, initial clean build, 2 warm up, 13 builds
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

    def "applies and reverts changes to Java source file while running benchmark"() {
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

        def scenarioFile = file("scenarios.conf")
        scenarioFile << """
classes {
    tasks = "help"
    apply-abi-change-to = "src/main/java/Library.java"
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--benchmark", "--scenario-file", scenarioFile.absolutePath)

        then:
        // Probe version, initial clean build, 2 warm up, 13 builds
        logFile.grep("<src-length: ${srcFile.length()}>").size() == 10
        logFile.grep("<src-length: ${srcFile.length() + 32}>").size() == 7
    }

    def "applies and reverts changes to Android resource file while running benchmark"() {
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

        def scenarioFile = file("scenarios.conf")
        scenarioFile << """
classes {
    tasks = "help"
    apply-android-resource-change-to = "src/main/res/values/strings.xml"
}
"""

        when:
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--benchmark", "--scenario-file", scenarioFile.absolutePath)

        then:
        // Probe version, initial clean build, 2 warm up, 13 builds
        logFile.grep("<src-length: ${srcFile.length()}>").size() == 10
        logFile.grep("<src-length: ${srcFile.length() + 47}>").size() == 7
    }

    def "reverts changes on benchmark failures"() {
        given:
        buildFile.text = """
apply plugin: 'java'
if (file('src/main/java/Library.java').text.contains('method')) {
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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
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
        new Main().run("--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath, "--gradle-version", gradleVersion,
                "--benchmark", "--gradle-user-home", "foobar", "help")

        then:
        logFile.grep("User home: " + new File("foobar").absolutePath)
    }

    static class LogFile {
        final List<String> lines

        LogFile(File logFile) {
            lines = logFile.readLines()
        }

        boolean contains(String str) {
            return grep(str).size() > 0
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
