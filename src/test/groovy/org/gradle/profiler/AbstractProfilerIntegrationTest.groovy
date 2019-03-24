package org.gradle.profiler


import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import com.google.common.collect.ImmutableList

abstract class AbstractProfilerIntegrationTest extends Specification {

    @Shared
    List<String> supportedGradleVersions = ["3.3", "3.4.1", "3.5", "4.0", "4.1", "4.2.1", "4.7", "5.2.1"]
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

    ReportFile getResultFile() {
        def f = new File(outputDir, "benchmark.csv")
        assert f.isFile()

        return new ReportFile(f)
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

    void assertBuildScanPublished(String buildScanPluginVersion) {
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
            lines = ImmutableList.copyOf(logFile.readLines())
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

        String getText() {
            lines.join("\n")
        }
    }

    static class ReportFile extends FileFixture {
        ReportFile(File logFile) {
            super(logFile)
        }
    }

    static class LogFile extends FileFixture {
        LogFile(File logFile) {
            super(logFile)
        }
    }
}
