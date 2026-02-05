package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest

class HeapDumpProfilerIntegrationTest extends AbstractProfilerIntegrationTest {

    def "heap dump profiler uses build-end by default"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "1",
            "--profile", "heap-dump",
            "assemble"
        )

        then:
        // Verify heap dumps were created (warmup + iteration)
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= 2

        def heapDumpFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-build-end-")
        }
        assert heapDumpFiles.size() >= 2
    }

    def "heap dump profiler captures config-end when requested"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "1",
            "--profile", "heap-dump",
            "--heap-dump-when", "config-end",
            "assemble"
        )

        then:
        // Verify config-end heap dumps were created (warmup + iteration)
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= 2

        def heapDumpFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-config-end-")
        }
        assert heapDumpFiles.size() >= 2

        // Verify it's a valid HPROF file
        def heapDump = heapDumpFiles.first()
        byte[] header = new byte[12]
        heapDump.withInputStream {
            it.read(header)
        }
        assert new String(header) == "JAVA PROFILE"
    }

    def "heap dump profiler captures both config-end and build-end when requested"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "1",
            "--profile", "heap-dump",
            "--heap-dump-when", "config-end,build-end",
            "assemble"
        )

        then:
        // Verify both types of heap dumps were created (4 total: 2 config-end + 2 build-end for warmup + iteration)
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= 4

        def configEndFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-config-end-")
        }
        assert configEndFiles.size() >= 2

        def buildEndFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-build-end-")
        }
        assert buildEndFiles.size() >= 2
    }

    def "heap dump profiler creates valid heap dump files"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "1",
            "--profile", "heap-dump",
            "--heap-dump-when", "config-end",
            "assemble"
        )

        then:
        // Verify heap dumps were created
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= 2

        def heapDumpFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-config-end-")
        }
        assert heapDumpFiles.size() >= 2

        // Verify all heap dumps are valid HPROF files
        heapDumpFiles.each { heapDump ->
            byte[] header = new byte[12]
            heapDump.withInputStream {
                it.read(header)
            }
            assert new String(header) == "JAVA PROFILE"
        }
    }

    def "heap dumps are created for each build iteration"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "2",
            "--profile", "heap-dump",
            "--heap-dump-when", "build-end",
            "assemble"
        )

        then:
        // Verify heap dumps created for warmup + 2 iterations = 3 total
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= 3

        def heapDumpFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-build-end-")
        }
        assert heapDumpFiles.size() >= 3
    }

    def "only one heap dump per build despite multiple method calls"() {
        given:
        // Create a multi-project build that might trigger methods multiple times
        settingsFile.text = """
            rootProject.name = "example-project"
            include 'subproject1'
            include 'subproject2'
        """
        buildFile.text = """
            apply plugin: BasePlugin
            println "<gradle-version: " + gradle.gradleVersion + ">"
        """
        new File(projectDir, "subproject1").mkdirs()
        new File(projectDir, "subproject1/build.gradle").text = """
            apply plugin: 'java'
        """
        new File(projectDir, "subproject2").mkdirs()
        new File(projectDir, "subproject2/build.gradle").text = """
            apply plugin: 'java'
        """

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--no-daemon",
            "--iterations", "1",
            "--profile", "heap-dump",
            "--heap-dump-when", "config-end",
            "assemble"
        )

        then:
        // Even with multiple projects, should only create one heap dump per build
        def heapDumpCreateMessages = logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at")
        assert heapDumpCreateMessages.size() == 1

        def heapDumpFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-config-end-")
        }
        assert heapDumpFiles.size() == 1
    }

    def "heap dump profiler works across warmup and measured builds"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "1",
            "--profile", "heap-dump",
            "--heap-dump-when", "config-end",
            "assemble"
        )

        then:
        // Verify heap dumps from both warmup and measured builds
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= 2

        def heapDumpFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.startsWith("gradle-config-end-")
        }
        assert heapDumpFiles.size() >= 2
    }

    def "profiler validates --heap-dump-when option"() {
        given:
        instrumentedBuildScript()

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--warmups", "1",
            "--iterations", "1",
            "--profile", "heap-dump",
            "--heap-dump-when", "invalid-value",
            "assemble"
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid --heap-dump-when value: 'invalid-value'")
        e.message.contains("Allowed values:")
        e.message.contains("config-end")
        e.message.contains("build-end")
    }
}
