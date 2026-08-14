package org.gradle.profiler.report

import org.gradle.profiler.BuildAction
import org.gradle.profiler.BuildScenarioResultImpl
import org.gradle.profiler.GradleBuildConfiguration
import org.gradle.profiler.Phase
import org.gradle.profiler.gradle.GradleBuildInvoker
import org.gradle.profiler.gradle.GradleScenarioDefinition
import org.gradle.profiler.gradle.RunTasksAction
import org.gradle.profiler.report.ResultWriterTestFixtures.TestInvocationResult
import org.gradle.profiler.report.ResultWriterTestFixtures.TestSample
import org.gradle.profiler.report.ResultWriterTestFixtures.TestScenarioContext
import org.gradle.profiler.result.BuildInvocationResult
import org.gradle.profiler.result.Sample
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class CsvGeneratorTest extends Specification {

    @Rule TemporaryFolder tmpDir

    int scenarioCounter

    def setup() {
        scenarioCounter = 0
    }

    def "wide format keeps existing output when scenarios have the same warmups and iterations"() {
        def result1 = scenarioResult(
            scenario("release", "Assemble Release", ":assemble", 2, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 100],
                [phase: Phase.WARM_UP, iteration: 2, execution: 90],
                [phase: Phase.MEASURE, iteration: 1, execution: 80],
                [phase: Phase.MEASURE, iteration: 2, execution: 70],
            ]
        )
        def result2 = scenarioResult(
            scenario("debug", "Assemble Debug", ":assembleDebug", 2, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 110],
                [phase: Phase.WARM_UP, iteration: 2, execution: 95],
                [phase: Phase.MEASURE, iteration: 1, execution: 85],
                [phase: Phase.MEASURE, iteration: 2, execution: 75],
            ]
        )

        expect:
        writeCsv(Format.WIDE, [result1, result2]) == """scenario,Assemble Release,Assemble Debug
version,Gradle 6.7,Gradle 6.7
tasks,:assemble,:assembleDebug
value,total execution time,total execution time
warm-up build #1,100.00,110.00
warm-up build #2,90.00,95.00
measured build #1,80.00,85.00
measured build #2,70.00,75.00
"""
    }

    def "wide format aligns measured builds when scenarios have different warmup counts"() {
        def shortWarmup = scenarioResult(
            scenario("short-warmup", "Short Warmup", ":short", 1, 2),
            [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 100, test: 120],
                [phase: Phase.MEASURE, iteration: 1, execution: 80, test: 88],
                [phase: Phase.MEASURE, iteration: 2, execution: 70, test: 77],
            ]
        )
        def longWarmup = scenarioResult(
            scenario("long-warmup", "Long Warmup", ":long", 2, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 200],
                [phase: Phase.WARM_UP, iteration: 2, execution: 190],
                [phase: Phase.MEASURE, iteration: 1, execution: 180],
                [phase: Phase.MEASURE, iteration: 2, execution: 170],
            ]
        )

        expect:
        writeCsv(Format.WIDE, [shortWarmup, longWarmup]) == """scenario,Short Warmup,Short Warmup,Long Warmup
version,Gradle 6.7,Gradle 6.7,Gradle 6.7
tasks,:short,:short,:long
value,total execution time,Test sample,total execution time
warm-up build #1,100.00,120.00,200.00
warm-up build #2,,,190.00
measured build #1,80.00,88.00,180.00
measured build #2,70.00,77.00,170.00
"""
    }

    def "wide format pads trailing rows to the header width when scenarios have different iteration counts"() {
        def twoMeasures = scenarioResult(
            scenario("two-measures", "Two Measures", ":two", 1, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 100],
                [phase: Phase.MEASURE, iteration: 1, execution: 90],
                [phase: Phase.MEASURE, iteration: 2, execution: 80],
            ]
        )
        def threeMeasures = scenarioResult(
            scenario("three-measures", "Three Measures", ":three", 1, 3),
            [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 200, test: 220],
                [phase: Phase.MEASURE, iteration: 1, execution: 190, test: 210],
                [phase: Phase.MEASURE, iteration: 2, execution: 180, test: 200],
                [phase: Phase.MEASURE, iteration: 3, execution: 170, test: 190],
            ]
        )

        when:
        def csv = writeCsv(Format.WIDE, [threeMeasures, twoMeasures])
        def lines = csv.readLines()
        def headerWidth = lines[0].split(",", -1).length

        then:
        lines.drop(4).every { it.split(",", -1).length == headerWidth }
        lines[-1] == "measured build #3,170.00,190.00,"
    }

    def "long format is unchanged for scenarios with different warmup counts"() {
        def shortWarmup = scenarioResult(
            scenario("short-warmup", "Short Warmup", ":short", 1, 2),
            [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 100, test: 120],
                [phase: Phase.MEASURE, iteration: 1, execution: 80, test: 88],
                [phase: Phase.MEASURE, iteration: 2, execution: 70, test: 77],
            ]
        )
        def longWarmup = scenarioResult(
            scenario("long-warmup", "Long Warmup", ":long", 2, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [phase: Phase.WARM_UP, iteration: 1, execution: 200],
                [phase: Phase.WARM_UP, iteration: 2, execution: 190],
                [phase: Phase.MEASURE, iteration: 1, execution: 180],
                [phase: Phase.MEASURE, iteration: 2, execution: 170],
            ]
        )

        expect:
        writeCsv(Format.LONG, [shortWarmup, longWarmup]) == """Scenario,Tool,Tasks,Phase,Iteration,Sample,Duration,Count
Short Warmup,Gradle 6.7,:short,WARM_UP,1,total execution time,100.00,1
Short Warmup,Gradle 6.7,:short,WARM_UP,1,Test sample,120.00,1
Short Warmup,Gradle 6.7,:short,MEASURE,1,total execution time,80.00,1
Short Warmup,Gradle 6.7,:short,MEASURE,1,Test sample,88.00,1
Short Warmup,Gradle 6.7,:short,MEASURE,2,total execution time,70.00,1
Short Warmup,Gradle 6.7,:short,MEASURE,2,Test sample,77.00,1
Long Warmup,Gradle 6.7,:long,WARM_UP,1,total execution time,200.00,1
Long Warmup,Gradle 6.7,:long,WARM_UP,2,total execution time,190.00,1
Long Warmup,Gradle 6.7,:long,MEASURE,1,total execution time,180.00,1
Long Warmup,Gradle 6.7,:long,MEASURE,2,total execution time,170.00,1
"""
    }

    private String writeCsv(Format format, List<BuildScenarioResultImpl<BuildInvocationResult>> results) {
        def outputFile = tmpDir.newFile("benchmark-${format}-${scenarioCounter}.csv")
        new CsvGenerator(outputFile, format).write(null, [getScenarios: { results }] as BenchmarkResult)
        outputFile.text.replace("\r\n", "\n")
    }

    private GradleScenarioDefinition scenario(String name, String title, String task, int warmUpCount, int buildCount) {
        def id = scenarioCounter++
        def config = new GradleBuildConfiguration(
            GradleVersion.version("6.7"),
            tmpDir.newFolder("${name}-${id}-gradle-home"),
            tmpDir.newFolder("${name}-${id}-java-home"),
            [],
            false,
            false
        )
        new GradleScenarioDefinition(
            name,
            title,
            GradleBuildInvoker.ToolingApi,
            config,
            new RunTasksAction([task]),
            BuildAction.NO_OP,
            [],
            [:],
            [],
            warmUpCount,
            buildCount,
            tmpDir.newFolder("${name}-${id}-output"),
            [],
            [],
            false
        )
    }

    private static BuildScenarioResultImpl<BuildInvocationResult> scenarioResult(
        GradleScenarioDefinition scenario,
        List<Sample<? super BuildInvocationResult>> samples,
        List<Map<String, ?>> builds
    ) {
        def result = new BuildScenarioResultImpl<BuildInvocationResult>(scenario, { samples })
        def context = new TestScenarioContext(scenario.name)
        builds.each { build ->
            long testTime = (build.test ?: build.execution) as long
            def buildContext = context.withBuild(build.phase as Phase, build.iteration as int)
            result.accept(new TestInvocationResult(buildContext, build.execution as long, testTime))
        }
        result
    }
}
