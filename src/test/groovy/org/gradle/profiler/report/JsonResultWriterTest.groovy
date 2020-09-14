package org.gradle.profiler.report


import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.BuildScenarioResultImpl
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.ScenarioDefinition
import org.gradle.profiler.Version
import org.gradle.profiler.result.BuildInvocationResult
import org.gradle.profiler.result.Sample
import spock.lang.Specification

import java.time.Duration
import java.util.function.Supplier

class JsonResultWriterTest extends Specification {
    int counter
    def stringWriter = new StringWriter()

    def setup() {
        counter = 0
    }

    def "can serialize scenario"() {
        def writer = new JsonResultWriter(true)
        def scenario1 = new TestScenarioDefinition("release", "Assemble Release", ":assemble")
        def scenario2 = new TestScenarioDefinition("debug", "Assemble Debug", ":assembleDebug")
        def result1 = new BuildScenarioResultImpl<BuildInvocationResult>(scenario1, null, [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE])
        result1.accept(new TestInvocationResult(scenario1.context.withBuild(Phase.WARM_UP, 1), 100, 120))
        result1.accept(new TestInvocationResult(scenario1.context.withBuild(Phase.WARM_UP, 2),  80, 100))
        result1.accept(new TestInvocationResult(scenario1.context.withBuild(Phase.MEASURE, 1),  75,  90))
        result1.accept(new TestInvocationResult(scenario1.context.withBuild(Phase.MEASURE, 2),  70,  85))
        result1.accept(new TestInvocationResult(scenario1.context.withBuild(Phase.MEASURE, 3),  72,  80))
        result1.accept(new TestInvocationResult(scenario1.context.withBuild(Phase.MEASURE, 4),  68,  88))
        def result2 = new BuildScenarioResultImpl<BuildInvocationResult>(scenario2, result1, [BuildInvocationResult.EXECUTION_TIME, TestSample.INSTANCE])
        result2.accept(new TestInvocationResult(scenario2.context.withBuild(Phase.WARM_UP, 1), 110, 220))
        result2.accept(new TestInvocationResult(scenario2.context.withBuild(Phase.WARM_UP, 2),  90, 200))
        result2.accept(new TestInvocationResult(scenario2.context.withBuild(Phase.MEASURE, 1),  85, 190))
        result2.accept(new TestInvocationResult(scenario2.context.withBuild(Phase.MEASURE, 2),  80, 185))

        when:
        writer.write([result1, result2], stringWriter)
        then:
        stringWriter.toString() == """{
  "environment": {
    "profilerVersion": "${Version.version}"
  },
  "scenarios": [
    {
      "definition": {
        "name": "release",
        "title": "Assemble Release",
        "displayName": "Assemble Release",
        "buildTool": "Test Tool",
        "tasks": ":assemble",
        "id": "release@0"
      },
      "samples": [
        {
          "name": "execution",
          "confidence": 0.0
        },
        {
          "name": "Test sample",
          "confidence": 0.0
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
        "displayName": "Assemble Debug",
        "buildTool": "Test Tool",
        "tasks": ":assembleDebug",
        "id": "debug@1"
      },
      "samples": [
        {
          "name": "execution",
          "confidence": 0.9359224935489405
        },
        {
          "name": "Test sample",
          "confidence": 0.9359224935489405
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
            return ((TestInvocationResult) result).testTime
        }
    }

    class TestScenarioDefinition extends ScenarioDefinition {
        final ScenarioContext context
        private final String tasks

        TestScenarioDefinition(
            String name,
            String title = name,
            String tasks,
            Supplier<BuildMutator> buildMutator = { null },
            int warmUpCount = 2,
            int buildCount = 4,
            File outputDir = null
        ) {
            super(name, title, buildMutator, warmUpCount, buildCount, outputDir, null)
            this.context = new TestScenarioContext("${name}@${counter++}")
            this.tasks = tasks
        }

        @Override
        String getDisplayName() {
            return getTitle()
        }

        @Override
        String getProfileName() {
            return null
        }

        @Override
        String getBuildToolDisplayName() {
            return "Test Tool"
        }

        @Override
        String getTasksDisplayName() {
            tasks
        }
    }

    class TestInvocationResult extends BuildInvocationResult {
        final Duration testTime
        final BuildContext context

        TestInvocationResult(BuildContext context, long executionTime, long testTime) {
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
        final int iteration;
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
}
