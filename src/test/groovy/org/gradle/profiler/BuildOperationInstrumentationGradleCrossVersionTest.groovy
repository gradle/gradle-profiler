package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest
import spock.lang.Requires

import static org.hamcrest.CoreMatchers.*
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.lessThan
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
        lines.get(3) == "value,total execution time,SnapshotTaskInputsBuildOperationType (Duration Sum)"

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
                    'scenario file using object with implicit kind',
                    [],
                    'measured-build-ops = [{type = "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"}]'
                ],
                [
                    'scenario file using object with explicit kind',
                    [],
                    'measured-build-ops = [{type = "org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType", measurement-kind = cumulative_time}]'
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
    def "can benchmark time to end of last configure build op via #via(configuration-cache: #configurationCache)"() {
        given:
        instrumentedBuildScript()
        println(commandLine)
        println(scenarioConfiguration)

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
        lines.get(3) == "value,total execution time,ConfigureBuildBuildOperationType (Time to Last Completed)"

        def firstWarmup = lines.get(4)
        def configureBuildOp = firstWarmup =~ /warm-up build #1,$SAMPLE,($SAMPLE)/
        configureBuildOp.matches()
        Double.valueOf(configureBuildOp[0][1]) > 1

        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE")

        where:
        [via, commandLine, scenarioConfiguration, configurationCache] << [
            [
                [
                    'command line',
                    ["--measure-build-op", "org.gradle.initialization.ConfigureBuildBuildOperationType:time_to_last_inclusive"],
                    null
                ],
                [
                    'scenario file',
                    [],
                    'measured-build-ops = [{type = "org.gradle.initialization.ConfigureBuildBuildOperationType", measurement-kind = time_to_last_inclusive}]'
                ],
                [
                    'command line and scenario file',
                    ["--measure-build-op", "org.gradle.initialization.ConfigureBuildBuildOperationType:time_to_last_inclusive"],
                    'measured-build-ops = [{type = "org.gradle.initialization.ConfigureBuildBuildOperationType", measurement-kind = time_to_last_inclusive}]'
                ]
            ],
            [false, true]
        ].combinations().collect { it[0] + it[1] }
    }

    @Requires({ it.instance.gradleVersionWithExperimentalConfigurationCache() })
    def "can benchmark configure build op in two ways via #via(configuration-cache: #configurationCache)"() {
        given:
        instrumentedBuildScript()
        println(commandLine)
        println(scenarioConfiguration)

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
        lines.get(0) == "scenario,default,default,default"
        lines.get(1) == "version,Gradle ${gradleVersion},Gradle ${gradleVersion},Gradle ${gradleVersion}"
        lines.get(2) == "tasks,assemble,assemble,assemble"
        lines.get(3) == "value,total execution time,ConfigureBuildBuildOperationType (Duration Sum),ConfigureBuildBuildOperationType (Time to Last Completed)"

        def firstWarmup = lines.get(4)
        def configureBuildOp = firstWarmup =~ /warm-up build #1,$SAMPLE,($SAMPLE),($SAMPLE)/
        configureBuildOp.matches()
        def durationSum = Double.valueOf(configureBuildOp[0][1])
        durationSum > 1
        def timeToLastCompleted = Double.valueOf(configureBuildOp[0][2])
        timeToLastCompleted > 1
        // With our project that only has one build, the duration sum should always be less than the time to last completed
        // As there is time before the configure build operation starts that is included in the time to last completed but not in the duration sum
        // This operates as a sanity check that the two measurements are being recorded in a consistent way
        assertThat(
            "duration sum should be less than time to last completed",
            durationSum,
            lessThan(timeToLastCompleted)
        )

        lines.get(9).matches("warm-up build #6,$SAMPLE,$SAMPLE,$SAMPLE")
        lines.get(10).matches("measured build #1,$SAMPLE,$SAMPLE,$SAMPLE")

        where:
        [via, commandLine, scenarioConfiguration, configurationCache] << [
            [
                [
                    'command line',
                    [
                        "--measure-build-op", "org.gradle.initialization.ConfigureBuildBuildOperationType:cumulative_time",
                        "--measure-build-op", "org.gradle.initialization.ConfigureBuildBuildOperationType:time_to_last_inclusive"
                    ],
                    null
                ],
                [
                    'scenario file',
                    [],
                    '''
                    measured-build-ops = [
                        {type = "org.gradle.initialization.ConfigureBuildBuildOperationType", measurement-kind = cumulative_time},
                        {type = "org.gradle.initialization.ConfigureBuildBuildOperationType", measurement-kind = time_to_last_inclusive},
                    ]
                    '''
                ],
                [
                    'command line and scenario file',
                    [
                        "--measure-build-op", "org.gradle.initialization.ConfigureBuildBuildOperationType:cumulative_time",
                        "--measure-build-op", "org.gradle.initialization.ConfigureBuildBuildOperationType:time_to_last_inclusive"
                    ],
                    '''
                    measured-build-ops = [
                        {type = "org.gradle.initialization.ConfigureBuildBuildOperationType", measurement-kind = cumulative_time},
                        {type = "org.gradle.initialization.ConfigureBuildBuildOperationType", measurement-kind = time_to_last_inclusive},
                    ]
                    '''
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
        lines.get(3) == "value,total execution time,task start,SnapshotTaskInputsBuildOperationType (Duration Sum)"
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
        lines.get(3) == "value,total execution time,NonExistentBuildOperationType (Duration Sum),SnapshotTaskInputsBuildOperationType (Duration Sum)"
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
        file("gradle.properties") << "\norg.gradle.caching=true"

        def sourceFile = file("src/main/java/A.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "class A {}"
    }
}
