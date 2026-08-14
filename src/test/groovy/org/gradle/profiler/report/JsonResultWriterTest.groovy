package org.gradle.profiler.report

import com.google.gson.Gson
import org.gradle.profiler.BuildAction
import org.gradle.profiler.BuildScenarioResultImpl
import org.gradle.profiler.GradleBuildConfiguration
import org.gradle.profiler.gradle.GradleBuildInvoker
import org.gradle.profiler.gradle.GradleScenarioDefinition
import org.gradle.profiler.OperatingSystem
import org.gradle.profiler.Phase
import org.gradle.profiler.gradle.RunTasksAction
import org.gradle.profiler.mutations.ApplyAbiChangeToKotlinSourceFileMutator
import org.gradle.profiler.result.BuildInvocationResult
import org.gradle.profiler.report.ResultWriterTestFixtures.TestInvocationResult
import org.gradle.profiler.report.ResultWriterTestFixtures.TestSample
import org.gradle.profiler.report.ResultWriterTestFixtures.TestScenarioContext
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Snapshot
import spock.lang.Snapshotter
import spock.lang.Specification

import java.time.Instant

class JsonResultWriterTest extends Specification {

    @Rule TemporaryFolder tmpDir

    @Snapshot(extension = 'json')
    Snapshotter snapshotter

    int counter
    def stringWriter = new StringWriter()

    def setup() {
        counter = 0
    }

    def "can serialize scenario"() {
        def writer = new JsonResultWriter(true)

        def gradleHomeDir = tmpDir.newFolder("gradle-home")
        def javaHomeDir = tmpDir.newFolder("java-home")
        def releaseOutputDir = tmpDir.newFolder("output-dir-release")
        def debugOutputDir = tmpDir.newFolder("output-dir-debug")
        def gradleVersion = GradleVersion.version("6.7")
        def sourceFile = tmpDir.newFile("Source.kt")
        def mutator = new ApplyAbiChangeToKotlinSourceFileMutator(sourceFile)

        def config = new GradleBuildConfiguration(
            gradleVersion,
            gradleHomeDir,
            javaHomeDir,
            ["-Xmx512m"],
            false,
            false
        )

        def scenario1 = new GradleScenarioDefinition(
            "release",
            "Assemble Release",
            GradleBuildInvoker.ToolingApi,
            config,
            new RunTasksAction([":assemble"]),
            BuildAction.NO_OP,
            ["-Palma=release"],
            ["org.gradle.test": "true"],
            [ mutator ],
            2,
            4,
            releaseOutputDir,
            ["-Xmx1024m"],
            ["some-build-op"],
            false
        )
        def scenarioContext1 = new TestScenarioContext("release@0")
        def scenario2 = new GradleScenarioDefinition(
            "debug",
            "Assemble Debug",
            GradleBuildInvoker.ToolingApi,
            config,
            new RunTasksAction([":assembleDebug"]),
            BuildAction.NO_OP,
            ["-Palma=debug"],
            ["org.gradle.test": "true"],
            [ mutator ],
            2,
            4,
            debugOutputDir,
            ["-Xmx1024m"],
            ["some-build-op"],
            true
        )
        def scenarioContext2 = new TestScenarioContext("debug@1")
        def result1 = new BuildScenarioResultImpl<BuildInvocationResult>(scenario1, { [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE] })
        result1.accept(new TestInvocationResult(scenarioContext1.withBuild(Phase.WARM_UP, 1), 100, 120))
        result1.accept(new TestInvocationResult(scenarioContext1.withBuild(Phase.WARM_UP, 2),  80, 100))
        result1.accept(new TestInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 1),  75,  90))
        result1.accept(new TestInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 2),  70,  85))
        result1.accept(new TestInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 3),  72,  80))
        result1.accept(new TestInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 4),  68,  88))
        def result2 = new BuildScenarioResultImpl<BuildInvocationResult>(scenario2, { [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE] })
        result2.accept(new TestInvocationResult(scenarioContext2.withBuild(Phase.WARM_UP, 1), 110, 220))
        result2.accept(new TestInvocationResult(scenarioContext2.withBuild(Phase.WARM_UP, 2),  90, 200))
        result2.accept(new TestInvocationResult(scenarioContext2.withBuild(Phase.MEASURE, 1),  85, 190))
        result2.accept(new TestInvocationResult(scenarioContext2.withBuild(Phase.MEASURE, 2),  80, 185))

        when:
        writer.write("Test benchmark", Instant.ofEpochMilli(1600000000000), [result1, result2], stringWriter)

        then:
        snapshotter.assertThat(normalize(stringWriter.toString(), [
            (gradleHomeDir.absolutePath): "<gradleHome>",
            (javaHomeDir.absolutePath): "<javaHome>",
            (sourceFile.absolutePath): "<sourceFile>",
            (OperatingSystem.getId()): "<operatingSystem>"
        ])).matchesSnapshot()
    }

    /**
     * Replaces environment-dependent values with placeholders, keeping the JSON structure intact.
     */
    private static String normalize(String json, Map<String, String> replacements) {
        replacements.each { value, placeholder ->
            json = json.replace(jsonEscaped(value), placeholder)
        }
        return json
    }

    private static String jsonEscaped(String value) {
        String quoted = new Gson().toJson(value)
        return quoted.substring(1, quoted.length() - 1)
    }
}
