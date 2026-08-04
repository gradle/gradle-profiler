package org.gradle.profiler.report

import org.gradle.profiler.BuildAction
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildScenarioResultImpl
import org.gradle.profiler.GradleBuildConfiguration
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.gradle.GradleBuildInvoker
import org.gradle.profiler.gradle.GradleScenarioDefinition
import org.gradle.profiler.gradle.RunTasksAction
import org.gradle.profiler.result.BuildActionResult
import org.gradle.profiler.result.BuildInvocationResult
import org.gradle.profiler.result.Sample
import org.gradle.profiler.result.SingleInvocationDurationSample
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.time.Duration

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
                [Phase.WARM_UP, 1, 100],
                [Phase.WARM_UP, 2, 90],
                [Phase.MEASURE, 1, 80],
                [Phase.MEASURE, 2, 70],
            ]
        )
        def result2 = scenarioResult(
            scenario("debug", "Assemble Debug", ":assembleDebug", 2, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [Phase.WARM_UP, 1, 110],
                [Phase.WARM_UP, 2, 95],
                [Phase.MEASURE, 1, 85],
                [Phase.MEASURE, 2, 75],
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
                [Phase.WARM_UP, 1, 100, 120],
                [Phase.MEASURE, 1, 80, 88],
                [Phase.MEASURE, 2, 70, 77],
            ]
        )
        def longWarmup = scenarioResult(
            scenario("long-warmup", "Long Warmup", ":long", 2, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [Phase.WARM_UP, 1, 200],
                [Phase.WARM_UP, 2, 190],
                [Phase.MEASURE, 1, 180],
                [Phase.MEASURE, 2, 170],
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
                [Phase.WARM_UP, 1, 100],
                [Phase.MEASURE, 1, 90],
                [Phase.MEASURE, 2, 80],
            ]
        )
        def threeMeasures = scenarioResult(
            scenario("three-measures", "Three Measures", ":three", 1, 3),
            [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE],
            [
                [Phase.WARM_UP, 1, 200, 220],
                [Phase.MEASURE, 1, 190, 210],
                [Phase.MEASURE, 2, 180, 200],
                [Phase.MEASURE, 3, 170, 190],
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
                [Phase.WARM_UP, 1, 100, 120],
                [Phase.MEASURE, 1, 80, 88],
                [Phase.MEASURE, 2, 70, 77],
            ]
        )
        def longWarmup = scenarioResult(
            scenario("long-warmup", "Long Warmup", ":long", 2, 2),
            [BuildInvocationResult.EXECUTION_TIME],
            [
                [Phase.WARM_UP, 1, 200],
                [Phase.WARM_UP, 2, 190],
                [Phase.MEASURE, 1, 180],
                [Phase.MEASURE, 2, 170],
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
        List<List> builds
    ) {
        def result = new BuildScenarioResultImpl<BuildInvocationResult>(scenario, { samples })
        def context = new TestScenarioContext(scenario.name)
        builds.each { build ->
            long testTime = build.size() > 3 ? build[3] as long : build[2] as long
            result.accept(new TestInvocationResult(context.withBuild(build[0] as Phase, build[1] as int), build[2] as long, testTime))
        }
        result
    }

    static class TestSample extends SingleInvocationDurationSample<BuildInvocationResult> {
        static final TestSample INSTANCE = new TestSample()

        private TestSample() {
            super("Test sample")
        }

        @Override
        protected Duration extractTotalDurationFrom(BuildInvocationResult result) {
            ((TestInvocationResult) result).testTime
        }
    }

    static class TestInvocationResult extends BuildInvocationResult {
        final Duration testTime

        TestInvocationResult(BuildContext context, long executionTime, long testTime) {
            super(context, new BuildActionResult(Duration.ofMillis(executionTime)))
            this.testTime = Duration.ofMillis(testTime)
        }
    }

    static class TestScenarioContext implements ScenarioContext {
        final String uniqueScenarioId

        TestScenarioContext(String uniqueScenarioId) {
            this.uniqueScenarioId = uniqueScenarioId
        }

        @Override
        BuildContext withBuild(Phase phase, int iteration) {
            new TestBuildContext(this, phase, iteration)
        }
    }

    static class TestBuildContext implements BuildContext {
        @Delegate
        private final ScenarioContext scenario
        final Phase phase
        final int iteration
        final String uniqueBuildId
        final String displayName

        TestBuildContext(ScenarioContext scenario, Phase phase, int iteration) {
            this.scenario = scenario
            this.phase = phase
            this.iteration = iteration
            this.uniqueBuildId = "${scenario.uniqueScenarioId}@${phase}@${iteration}"
            this.displayName = phase.displayBuildNumber(iteration)
        }
    }
}
