package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest
import spock.lang.Requires

import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

@Requires({ it.instance.gradleVersionWithAdvancedBenchmarking() })
class BuildOperationInstrumentationGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    def setup() {
        defaultWarmupsAndIterations()
    }

    @Requires({ it.instance.gradleVersionWithExperimentalConfigurationCache() })
    def "can benchmark GC time(configuration-cache: #configurationCache)"() {
        given:
        instrumentedBuildScript()

        buildFile << """
            assemble.doLast {
                System.gc()
            }
        """

        and:
        def args = [
            "--gradle-version", gradleVersion,
            "--benchmark",
            "--measure-gc",
            "assemble"
        ]
        if (configurationCache) {
            file("gradle.properties") << """
                org.gradle.unsafe.configuration-cache=true
            """
        }

        when:
        run(args)

        then:
        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,Gradle ${gradleVersion},Gradle ${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,total execution time,garbage collection time"
        lines.get(4).matches("warm-up build #1,$SAMPLE,$SAMPLE")
        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE")

        and:
        def gcTime = lines.get(10) =~ /measured build #1,$SAMPLE,($SAMPLE)/
        gcTime.matches()
        Double.valueOf(gcTime[0][1]) > 0

        where:
        configurationCache << [false, true]
    }

    @Requires({ it.instance.gradleVersionWithExperimentalConfigurationCache() })
    def "can benchmark local build cache size(configuration-cache: #configurationCache)"() {
        given:
        instrumentedBuildScript()

        buildFile << """
            // Produce some output so the cache size is noticeable
            task producePayload() {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                outputs.cacheIf { true }
                doLast {
                    def buffer = new byte[1024 * 1024]
                    new Random().nextBytes(buffer)
                    outputFile.bytes = buffer
                }
            }

            assemble.dependsOn producePayload
        """
        def scenarioFile = file("performance.scenarios")
        file("performance.scenarios") << """
            default {
                tasks = ["assemble"]
                clear-build-cache-before = SCENARIO
            }
        """

        and:
        def args = [
            "--gradle-version", gradleVersion,
            "--benchmark",
            "--measure-local-build-cache",
            "--scenario-file", scenarioFile.absolutePath,
            "default"
        ]
        file("gradle.properties") << """
            org.gradle.caching=true
            ${configurationCache ? "org.gradle.unsafe.configuration-cache=true" : ""}
        """

        when:
        run(args)

        then:
        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,Gradle ${gradleVersion},Gradle ${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,total execution time,local build cache size"
        lines.get(4).matches("warm-up build #1,$SAMPLE,$SAMPLE")
        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE")

        and:
        def localBuildCacheSize = lines.get(10) =~ /measured build #1,$SAMPLE,($SAMPLE)/
        localBuildCacheSize.matches()
        Double.valueOf(localBuildCacheSize[0][1]) > 0

        where:
        configurationCache << [false, true]
    }

    @Requires({ it.instance.gradleVersionWithExperimentalConfigurationCache() })
    def "can benchmark configuration time(configuration-cache: #configurationCache)"() {
        given:
        instrumentedBuildScript()

        and:
        def args = [
            "--gradle-version", gradleVersion,
            "--benchmark",
            "--measure-config-time",
            "assemble"
        ]
        if (configurationCache) {
            file("gradle.properties") << """
                org.gradle.unsafe.configuration-cache=true
            """
        }

        when:
        run(args)

        then:
        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,Gradle ${gradleVersion},Gradle ${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,total execution time,task start"
        lines.get(4).matches("warm-up build #1,$SAMPLE,$SAMPLE")
        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE")

        and:
        def taskStart = lines.get(10) =~ /measured build #1,$SAMPLE,($SAMPLE)/
        taskStart.matches()
        Double.valueOf(taskStart[0][1]) > 0

        where:
        configurationCache << [false, true]
    }

    @Requires({ it.instance.gradleVersionWithExperimentalConfigurationCache() })
    def "can benchmark snapshotting build operation time via #via(configuration-cache: #configurationCache)"() {
        given:
        instrumentedBuildForSnapshottingBenchmark()
        println(via)
        println(commandLine)
        println(scenarioConfiguration)
        println(configurationCache)

        and:
        def args = [
            "--gradle-version", gradleVersion,
            "--benchmark"
        ]
        if (scenarioConfiguration) {
            def scenarioFile = file("performance.scenarios")
            scenarioFile.text = """
            default {
                tasks = ["assemble"]
                ${scenarioConfiguration}
            }
            """
            args += ["--scenario-file", scenarioFile.absolutePath]
        }
        args += commandLine
        if (configurationCache) {
            file("gradle.properties") << """
                org.gradle.unsafe.configuration-cache=true
            """
        }
        args += scenarioConfiguration ? "default" : "assemble"

        when:
        run(args)

        then:
        def lines = resultFile.lines
        lines.size() == totalLinesForExecutions(16)
        lines.get(0) == "scenario,default,default"
        lines.get(1) == "version,Gradle ${gradleVersion},Gradle ${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble"
        lines.get(3) == "value,total execution time,SnapshotTaskInputsBuildOperationType"

        def firstWarmup = lines.get(4)
        def snapshottingDuration = firstWarmup =~ /warm-up build #1,$SAMPLE,($SAMPLE)/
        snapshottingDuration.matches()
        Double.valueOf(snapshottingDuration[0][1]) > 0

        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE")

        where:
        [via, commandLine, scenarioConfiguration, configurationCache] << [
            [
                [
                    'command line',
                    ["--measure-build-op", "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"],
                    null
                ],
                [
                    'scenario file',
                    [],
                    'measured-build-ops = ["org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"]'
                ],
                [
                    'command line and scenario file',
                    ["--measure-build-op", "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"],
                    'measured-build-ops = ["org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"]'
                ]
            ],
            [false, true]
        ].combinations().collect { it[0] + it[1] }
    }

    @Requires({ it.instance.gradleVersionWithExperimentalConfigurationCache() })
    def "can combine measuring configuration time and build operation(configuration-cache: #configurationCache)"() {
        given:
        instrumentedBuildForSnapshottingBenchmark()

        and:
        def args = [
            "--gradle-version", gradleVersion,
            "--benchmark",
            "--measure-config-time",
            "--measure-build-op", "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType",
            "assemble"
        ]
        if (configurationCache) {
            file("gradle.properties") << """
                org.gradle.unsafe.configuration-cache=true
            """
        }

        when:
        run(args)

        then:
        def lines = resultFile.lines
        assertThat(lines.size(), equalTo(totalLinesForExecutions(16)))
        lines.get(0) == "scenario,default,default,default"
        lines.get(1) == "version,Gradle ${gradleVersion},Gradle ${gradleVersion},Gradle ${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble,assemble"
        lines.get(3) == "value,total execution time,task start,SnapshotTaskInputsBuildOperationType"
        lines.get(4).matches("warm-up build #1,$SAMPLE,$SAMPLE,$SAMPLE")
        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE,$SAMPLE")

        and:
        def buildLines = lines.subList(10, 19).collect { it.tokenize(',') }
        def executions = buildLines.collect { Double.valueOf(it.get(1)) }
        def taskStarts = buildLines.collect { Double.valueOf(it.get(2)) }
        def buildOps = buildLines.collect { Double.valueOf(it.get(3)) }
        assertThat("non zero execution times", executions, hasItem(not(equalTo(0D))))
        assertThat("non zero task start times", taskStarts, hasItem(not(equalTo(0D))))
        assertThat("non zero build-op times", buildOps, hasItem(not(equalTo(0D))))
        assertTrue("different execution times", executions.size() == 9)
        assertTrue("different task start times", taskStarts.size() == 9)
        assertTrue("different build-op times", buildOps.size() == 9)

        where:
        configurationCache << [false, true]
    }

    @Requires({ it.instance.gradleVersionWithAdvancedBenchmarking() })
    def "gracefully ignores non-existent build-operation"() {
        given:
        instrumentedBuildForSnapshottingBenchmark()

        and:
        def args = [
            "--gradle-version", gradleVersion,
            "--benchmark",
            "--measure-build-op", "org.gradle.api.internal.NonExistentBuildOperationType",
            "--measure-build-op", "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType",
            "assemble"
        ]

        when:
        run(args)

        then:
        def lines = resultFile.lines
        assertThat(lines.size(), equalTo(totalLinesForExecutions(16)))
        lines.get(0) == "scenario,default,default,default"
        lines.get(1) == "version,Gradle ${gradleVersion},Gradle ${gradleVersion},Gradle ${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble,assemble"
        lines.get(3) == "value,total execution time,NonExistentBuildOperationType,SnapshotTaskInputsBuildOperationType"
        lines.get(4).matches("warm-up build #1,$SAMPLE,$SAMPLE,$SAMPLE")
        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE,$SAMPLE")

        and:
        def buildLines = lines.subList(10, 19).collect { it.tokenize(',') }
        def executions = buildLines.collect { Double.valueOf(it.get(1)) }
        def buildNoops = buildLines.collect { Double.valueOf(it.get(2)) }
        def buildOps = buildLines.collect { Double.valueOf(it.get(3)) }
        assertThat("non zero execution times", executions, hasItem(not(equalTo(0D))))
        assertThat("zero times for non-existent build-op", buildNoops, everyItem(equalTo(0D)))
        assertThat("non zero build-op times", buildOps, hasItem(not(equalTo(0D))))
        assertTrue("different execution times", executions.size() == 9)
        assertTrue("different build-op times", buildOps.size() == 9)
    }

    private void instrumentedBuildForSnapshottingBenchmark() {
        instrumentedBuildScript()
        buildFile << """
            apply plugin: 'java'
        """

        // We don't capture snapshotting time (yet) if the build cache is not enabled
        file("gradle.properties").text = "org.gradle.caching=true"

        def sourceFile = file("src/main/java/A.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "class A {}"
    }
}
