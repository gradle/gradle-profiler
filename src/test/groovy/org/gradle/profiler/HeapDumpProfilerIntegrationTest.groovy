package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import spock.lang.Unroll

class HeapDumpProfilerIntegrationTest extends AbstractProfilerIntegrationTest {

    @Unroll
    def "heap dump profiler captures #strategy strategy"() {
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
            "--heap-dump-when", strategy,
            "assemble"
        )

        then:
        // Verify heap dumps were created (warmup + iteration)
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= expectedMinDumps

        def heapDumpFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.contains("-${expectedFileNamePart}")
        }
        assert heapDumpFiles.size() >= expectedMinDumps

        where:
        strategy      | expectedFileNamePart | expectedMinDumps
        "config-end"  | "config-end"         | 2
        "build-end"   | "build-end"          | 1
        "cc"          | "cc-dump"            | 2
    }

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
            it.name.endsWith(".hprof") && it.name.contains("-build-end")
        }
        assert heapDumpFiles.size() >= 1
    }

    @Unroll
    def "heap dump profiler captures combination: #strategies"() {
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
            "--heap-dump-when", strategies,
            "assemble"
        )

        then:
        // Verify all expected heap dumps were created
        logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size() >= expectedTotalDumps

        // Verify each expected type exists
        expectedTypes.each { type ->
            def files = outputDir.listFiles().findAll {
                it.name.endsWith(".hprof") && it.name.contains("-${type}")
            }
            assert files.size() >= expectedMinPerType[type], "Expected at least ${expectedMinPerType[type]} files for ${type}, but found ${files.size()}"
        }

        where:
        strategies                  | expectedTypes                    | expectedMinPerType                                | expectedTotalDumps
        "config-end,build-end"      | ["config-end", "build-end"]      | ["config-end": 2, "build-end": 1]                 | 4
        "cc,build-end"              | ["cc-dump", "build-end"]         | ["cc-dump": 2, "build-end": 1]                    | 4
        "cc,config-end"             | ["cc-dump", "config-end"]        | ["cc-dump": 2, "config-end": 2]                   | 4
        "config-end,build-end,cc"   | ["config-end", "build-end", "cc-dump"] | ["config-end": 2, "build-end": 1, "cc-dump": 2] | 6
    }

    def "heap dump profiler with all strategies enabled produces all expected dumps"() {
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
            "--heap-dump-when", "config-end,build-end,cc",
            "assemble"
        )

        then:
        // With 1 warmup + 2 iterations = 3 builds
        // Each build should produce: 1 config-end + 1 build-end + 1 cc = 3 dumps per build
        // Total expected: 3 builds * 3 dumps = 9 dumps
        def totalDumps = logFile.find("HEAP_DUMP_EXECUTOR: Creating heap dump at").size()
        assert totalDumps >= 9

        def configEndFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.contains("-config-end")
        }
        assert configEndFiles.size() >= 3, "Expected at least 3 config-end dumps, got ${configEndFiles.size()}"

        def buildEndFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.contains("-build-end")
        }
        assert buildEndFiles.size() >= 3, "Expected at least 3 build-end dumps, got ${buildEndFiles.size()}"

        def ccFiles = outputDir.listFiles().findAll {
            it.name.endsWith(".hprof") && it.name.contains("-cc-dump")
        }
        assert ccFiles.size() >= 3, "Expected at least 3 cc dumps, got ${ccFiles.size()}"

        // Verify all dumps are valid HPROF files
        [configEndFiles, buildEndFiles, ccFiles].flatten().each { heapDump ->
            byte[] header = new byte[12]
            heapDump.withInputStream {
                it.read(header)
            }
            assert new String(header) == "JAVA PROFILE"
        }
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
            it.name.endsWith(".hprof") && it.name.contains("-config-end")
        }
        assert heapDumpFiles.size() >= 1

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
            it.name.endsWith(".hprof") && it.name.contains("-build-end")
        }
        assert heapDumpFiles.size() >= 3
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
            it.name.endsWith(".hprof") && it.name.contains("-config-end")
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
        e.message.contains("cc")
    }
}
