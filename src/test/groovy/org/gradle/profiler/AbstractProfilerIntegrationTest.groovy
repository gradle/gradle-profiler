package org.gradle.profiler

import org.gradle.api.JavaVersion
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared

import java.util.regex.Pattern

abstract class AbstractProfilerIntegrationTest extends AbstractIntegrationTest {

    private static int NUMBER_OF_HEADERS = 4

    static final SAMPLE = "-?\\d+(?:\\.\\d+)"

    static List<String> gradleVersionsSupportedOnCurrentJvm(List<String> gradleVersions) {
        gradleVersions.findAll {
            JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17) ? GradleVersion.version(it) >= GradleVersion.version("7.3") : true
        }
    }

    @Shared
    List<String> supportedGradleVersions = gradleVersionsSupportedOnCurrentJvm([
        "3.3", "3.5",
        "4.0", "4.7",
        "5.2.1", "5.6.3",
        "6.0.1", "6.1", "6.6.1",
        "7.1.1", "7.6.4"
    ])

    @Shared
    String minimalSupportedGradleVersion = supportedGradleVersions.first()
    @Shared
    String latestSupportedGradleVersion = supportedGradleVersions.last()

    static String buildScanPluginVersion(String gradleVersion) {
        (GradleVersion.version(gradleVersion) < GradleVersion.version("5.0")) ? '1.16' : '3.5'
    }

    static String transformCacheLocation(String gradleVersionString) {
        def gradleVersion = GradleVersion.version(gradleVersionString)
        if (gradleVersion < GradleVersion.version("5.1")) {
            return 'transforms-1/files-1.1'
        }
        if (gradleVersion < GradleVersion.version('6.8')) {
            return 'transforms-2/files-2.1'
        }
        return "transforms-3"
    }

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    File projectDir
    File outputDir

    LogFile getLogFile() {
        def f = new File(outputDir, "profile.log")
        assert f.isFile()
        return new LogFile(f)
    }

    ReportFile getResultFile() {
        def f = new File(outputDir, "benchmark.csv")
        assert f.isFile()

        return new ReportFile(f)
    }

    File getBuildFile() {
        return new File(projectDir, "build.gradle")
    }

    File getSettingsFile() {
        return new File(projectDir, "settings.gradle")
    }

    File file(String path) {
        return new File(projectDir, path)
    }

    def setup() {
        projectDir = tmpDir.newFolder()
        outputDir = tmpDir.newFolder()
        settingsFile.text = """
            rootProject.name = "example-project"
        """
    }

    void assertBuildScanPublished(String buildScanPluginVersion = null) {
        if (buildScanPluginVersion) {
            assert logFile.find("Using build scan plugin " + buildScanPluginVersion).size() == 1
        } else {
            assert logFile.find("Using build scan plugin specified in the build").size() == 1
        }
        assert logFile.find("Publishing build").size() == 1: ("LOG FILE:" + logFile.text)
    }

    def instrumentedBuildScript() {
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
println "<invocations: " + (++Counter.invocations) + ">"

class Counter {
    static int invocations = 0
}
"""
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

    def createSimpleBazelWorkspace() {
        new File(projectDir, "WORKSPACE").createNewFile()
        new File(projectDir, "BUILD").text = '''
genrule(
  name = "hello",
  outs = ["hello_world.txt"],
  cmd = "echo Hello World > $@",
)'''
    }

    static class FileFixture {
        final List<String> lines

        FileFixture(File logFile) {
            lines = logFile.readLines()
        }

        @Override
        String toString() {
            return lines.join("\n")
        }

        /**
         * Contains a single line with the given text.
         */
        boolean containsOne(String str) {
            return find(str).size() == 1
        }

        /**
         * Locates the lines containing the given string
         */
        List<String> find(String str) {
            lines.findAll { it.contains(str) }
        }

        /**
         * Locates the lines containing the given pattern
         */
        List<String> find(Pattern pattern) {
            lines.findAll { it.matches(pattern) }
        }

        String getText() {
            lines.join("\n")
        }
    }

    static class ReportFile extends FileFixture {
        ReportFile(File logFile) {
            super(logFile)
        }

        /**
         * Asserts that this report file contains a single benchmarking scenario that uses a warm daemon.
         */
        void containsWarmDaemonScenario(String gradleVersion, String name = "default", List<String> tasks) {
            def lines = this.lines
            assert lines.size() == totalLinesForExecutions(16)
            assert lines.get(0) == "scenario,${name}"
            assert lines.get(1) == "version,Gradle ${gradleVersion}"
            assert lines.get(2) == "tasks,${tasks.join(", ")}"
            assert lines.get(3) == "value,total execution time"
            assert lines.get(4).matches("warm-up build #1,$SAMPLE")
            assert lines.get(9).matches("warm-up build #6,$SAMPLE")
            assert lines.get(10).matches("measured build #1,$SAMPLE")
            assert lines.get(11).matches("measured build #2,$SAMPLE")
            assert lines.get(19).matches("measured build #10,$SAMPLE")
        }

        /**
         * Asserts that this report file contains a single benchmarking scenario that uses a cold daemon.
         */
        void containsColdDaemonScenario(String gradleVersion, String name = "default", List<String> tasks) {
            def lines = this.lines
            assert lines.size() == totalLinesForExecutions(11)
            assert lines.get(0) == "scenario,${name}"
            assert lines.get(1) == "version,Gradle ${gradleVersion}"
            assert lines.get(2) == "tasks,${tasks.join(", ")}"
            assert lines.get(3) == "value,total execution time"
            assert lines.get(4).matches("warm-up build #1,$SAMPLE")
            assert lines.get(5).matches("measured build #1,$SAMPLE")
            assert lines.get(6).matches("measured build #2,$SAMPLE")
            assert lines.get(14).matches("measured build #10,$SAMPLE")
        }

        /**
         * Asserts that this report file contains a single benchmarking scenario that uses no daemon.
         */
        void containsNoDaemonScenario(String gradleVersion, String name = "default", List<String> tasks) {
            def lines = this.lines
            assert lines.size() == totalLinesForExecutions(11)
            assert lines.get(0) == "scenario,${name}"
            assert lines.get(1) == "version,Gradle ${gradleVersion}"
            assert lines.get(2) == "tasks,${tasks.join(", ")}"
            assert lines.get(3) == "value,total execution time"
            assert lines.get(4).matches("warm-up build #1,$SAMPLE")
            assert lines.get(5).matches("measured build #1,$SAMPLE")
            assert lines.get(6).matches("measured build #2,$SAMPLE")
            assert lines.get(14).matches("measured build #10,$SAMPLE")
        }
    }

    static class LogFile extends FileFixture {
        LogFile(File logFile) {
            super(logFile)
        }

        /**
         * Asserts that this log file contains a single benchmarking scenario that uses a warm daemon.
         */
        void containsWarmDaemonScenario(String gradleVersion, String name = null, List<String> tasks) {
            // Probe version, 6 warm up, 10 builds
            containsScenario(name, gradleVersion)
            assert find("* Running warm-up build").size() == 6
            assert find("* Running measured build").size() == 10
            assert find("<gradle-version: $gradleVersion>").size() == 17
            assert find("<daemon: true").size() == 17
            assert find("<daemon: false").size() == 0
            assert find("<tasks: [:help]>").size() == 1
            assert find("<tasks: [${tasks.join(", ")}]>").size() == 16
            assert containsOne("<invocations: 16>")
        }

        /**
         * Asserts that this log file contains a single benchmarking scenario that uses a cold daemon.
         */
        void containsColdDaemonScenario(String gradleVersion, String name = null, List<String> tasks) {
            // Probe version, 1 warm up, 10 builds
            containsScenario(name, gradleVersion)
            assert find("* Running warm-up build").size() == 1
            assert find("* Running measured build").size() == 10
            assert find("<gradle-version: $gradleVersion>").size() == 12
            assert find("<daemon: true").size() == 12
            assert find("<daemon: false").size() == 0
            assert find("<tasks: [:help]>").size() == 1
            assert find("<tasks: [${tasks.join(", ")}]>").size() == 11
            assert find("<invocations: 1>").size() == 12
        }

        /**
         * Asserts that this log file contains a single benchmarking scenario that uses no daemon.
         */
        void containsNoDaemonScenario(String gradleVersion, String name = null, List<String> tasks) {
            // Probe version, 1 warm up, 10 builds
            containsScenario(name, gradleVersion)
            assert find("* Running warm-up build").size() == 1
            assert find("* Running measured build").size() == 10
            assert find("<gradle-version: $gradleVersion>").size() == 12
            assert find("<daemon: true").size() == 1
            assert find("<daemon: false").size() == 11
            assert find("<tasks: [:help]>").size() == 1
            assert find("<tasks: [${tasks.join(", ")}]>").size() == 11
            assert find("<invocations: 1>").size() == 12
        }

        private containsScenario(String name, String gradleVersion) {
            def display = name == null ? "scenario" : "scenario ${name}"
            assert containsOne("* Running $display using Gradle $gradleVersion (scenario 1/1)")
        }
    }

    static int totalLinesForExecutions(int numberOfExecutions) {
        return NUMBER_OF_HEADERS + numberOfExecutions
    }
}
