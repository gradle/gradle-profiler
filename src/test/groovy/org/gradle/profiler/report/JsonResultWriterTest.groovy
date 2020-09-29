package org.gradle.profiler.report

import com.google.gson.Gson
import org.gradle.profiler.BuildAction
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildScenarioResultImpl
import org.gradle.profiler.GradleBuildConfiguration
import org.gradle.profiler.GradleBuildInvoker
import org.gradle.profiler.GradleScenarioDefinition
import org.gradle.profiler.OperatingSystem
import org.gradle.profiler.Phase
import org.gradle.profiler.RunTasksAction
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.mutations.ApplyAbiChangeToKotlinSourceFileMutator
import org.gradle.profiler.result.BuildInvocationResult
import org.gradle.profiler.result.Sample
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class JsonResultWriterTest extends Specification {

    @Rule TemporaryFolder tmpDir

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
            ["some-build-op"]
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
            ["some-build-op"]
        )
        def scenarioContext2 = new TestScenarioContext("debug@1")
        def result1 = new BuildScenarioResultImpl<BuildInvocationResult>(scenario1, [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE])
        result1.accept(new GradleInvocationResult(scenarioContext1.withBuild(Phase.WARM_UP, 1), 100, 120))
        result1.accept(new GradleInvocationResult(scenarioContext1.withBuild(Phase.WARM_UP, 2),  80, 100))
        result1.accept(new GradleInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 1),  75,  90))
        result1.accept(new GradleInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 2),  70,  85))
        result1.accept(new GradleInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 3),  72,  80))
        result1.accept(new GradleInvocationResult(scenarioContext1.withBuild(Phase.MEASURE, 4),  68,  88))
        def result2 = new BuildScenarioResultImpl<BuildInvocationResult>(scenario2, [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE])
        result2.accept(new GradleInvocationResult(scenarioContext2.withBuild(Phase.WARM_UP, 1), 110, 220))
        result2.accept(new GradleInvocationResult(scenarioContext2.withBuild(Phase.WARM_UP, 2),  90, 200))
        result2.accept(new GradleInvocationResult(scenarioContext2.withBuild(Phase.MEASURE, 1),  85, 190))
        result2.accept(new GradleInvocationResult(scenarioContext2.withBuild(Phase.MEASURE, 2),  80, 185))

        when:
        writer.write("Test benchmark", Instant.ofEpochMilli(1600000000000), [result1, result2], stringWriter)
        then:
        stringWriter.toString() == """{
  "title": "Test benchmark",
  "date": "2020-09-13T12:26:40Z",
  "environment": {
    "profilerVersion": "UNKNOWN",
    "operatingSystem": "${OperatingSystem.getId()}"
  },
  "scenarios": [
    {
      "definition": {
        "name": "release",
        "title": "Assemble Release",
        "displayName": "Assemble Release using Gradle 6.7",
        "buildTool": "Gradle 6.7",
        "tasks": ":assemble",
        "version": "6.7",
        "gradleHome": ${escape(gradleHomeDir.absolutePath)},
        "javaHome": ${escape(javaHomeDir.absolutePath)},
        "usesScanPlugin": false,
        "action": "run tasks :assemble",
        "cleanup": "do nothing",
        "invoker": "Tooling API",
        "mutators": [
          ${escape("ApplyAbiChangeToKotlinSourceFileMutator(${sourceFile.absolutePath})")}
        ],
        "args": [
          "-Palma\\u003drelease"
        ],
        "jvmArgs": [
          "-Xmx512m",
          "-Xmx1024m"
        ],
        "systemProperties": {
          "org.gradle.test": "true"
        },
        "id": "release@0"
      },
      "samples": [
        {
          "name": "execution"
        },
        {
          "name": "Test sample"
        }
      ],
      "iterations": [
        {
          "id": "release@0@WARM_UP@1",
          "phase": "WARM_UP",
          "iteration": 1,
          "title": "warm-up build #1",
          "values": {
            "execution": 100.0,
            "Test sample": 120.0
          }
        },
        {
          "id": "release@0@WARM_UP@2",
          "phase": "WARM_UP",
          "iteration": 2,
          "title": "warm-up build #2",
          "values": {
            "execution": 80.0,
            "Test sample": 100.0
          }
        },
        {
          "id": "release@0@MEASURE@1",
          "phase": "MEASURE",
          "iteration": 1,
          "title": "measured build #1",
          "values": {
            "execution": 75.0,
            "Test sample": 90.0
          }
        },
        {
          "id": "release@0@MEASURE@2",
          "phase": "MEASURE",
          "iteration": 2,
          "title": "measured build #2",
          "values": {
            "execution": 70.0,
            "Test sample": 85.0
          }
        },
        {
          "id": "release@0@MEASURE@3",
          "phase": "MEASURE",
          "iteration": 3,
          "title": "measured build #3",
          "values": {
            "execution": 72.0,
            "Test sample": 80.0
          }
        },
        {
          "id": "release@0@MEASURE@4",
          "phase": "MEASURE",
          "iteration": 4,
          "title": "measured build #4",
          "values": {
            "execution": 68.0,
            "Test sample": 88.0
          }
        }
      ]
    },
    {
      "definition": {
        "name": "debug",
        "title": "Assemble Debug",
        "displayName": "Assemble Debug using Gradle 6.7",
        "buildTool": "Gradle 6.7",
        "tasks": ":assembleDebug",
        "version": "6.7",
        "gradleHome": ${escape(gradleHomeDir.absolutePath)},
        "javaHome": ${escape(javaHomeDir.absolutePath)},
        "usesScanPlugin": false,
        "action": "run tasks :assembleDebug",
        "cleanup": "do nothing",
        "invoker": "Tooling API",
        "mutators": [
          ${escape("ApplyAbiChangeToKotlinSourceFileMutator(${sourceFile.absolutePath})")}
        ],
        "args": [
          "-Palma\\u003ddebug"
        ],
        "jvmArgs": [
          "-Xmx512m",
          "-Xmx1024m"
        ],
        "systemProperties": {
          "org.gradle.test": "true"
        },
        "id": "debug@1"
      },
      "samples": [
        {
          "name": "execution"
        },
        {
          "name": "Test sample"
        }
      ],
      "iterations": [
        {
          "id": "debug@1@WARM_UP@1",
          "phase": "WARM_UP",
          "iteration": 1,
          "title": "warm-up build #1",
          "values": {
            "execution": 110.0,
            "Test sample": 220.0
          }
        },
        {
          "id": "debug@1@WARM_UP@2",
          "phase": "WARM_UP",
          "iteration": 2,
          "title": "warm-up build #2",
          "values": {
            "execution": 90.0,
            "Test sample": 200.0
          }
        },
        {
          "id": "debug@1@MEASURE@1",
          "phase": "MEASURE",
          "iteration": 1,
          "title": "measured build #1",
          "values": {
            "execution": 85.0,
            "Test sample": 190.0
          }
        },
        {
          "id": "debug@1@MEASURE@2",
          "phase": "MEASURE",
          "iteration": 2,
          "title": "measured build #2",
          "values": {
            "execution": 80.0,
            "Test sample": 185.0
          }
        }
      ]
    }
  ]
}"""
    }

    static class TestSample implements Sample<BuildInvocationResult> {
        static final TestSample INSTANCE = new TestSample()
        final String name = "Test sample"

        @Override
        Duration extractFrom(BuildInvocationResult result) {
            return ((GradleInvocationResult) result).testTime
        }
    }

    class GradleInvocationResult extends BuildInvocationResult {
        final Duration testTime
        final BuildContext context

        GradleInvocationResult(BuildContext context, long executionTime, long testTime) {
            super(context, Duration.ofMillis(executionTime))
            this.testTime = Duration.ofMillis(testTime)
            this.context = context
        }
    }

    class TestScenarioContext implements ScenarioContext {
        final String uniqueScenarioId

        TestScenarioContext(String uniqueScenarioId) {
            this.uniqueScenarioId = uniqueScenarioId
        }

        @Override
        BuildContext withBuild(Phase phase, int iteration) {
            new TestBuildContext(this, phase, iteration)
        }
    }

    class TestBuildContext implements BuildContext {
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
            this.displayName = phase.displayBuildNumber(getIteration())
        }
    }

    private static String escape(String string) {
        new Gson().toJson(string)
    }
}
