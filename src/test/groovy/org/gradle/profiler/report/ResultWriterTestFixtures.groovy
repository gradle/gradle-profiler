package org.gradle.profiler.report

import org.gradle.profiler.BuildContext
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.result.BuildActionResult
import org.gradle.profiler.result.BuildInvocationResult
import org.gradle.profiler.result.SingleInvocationDurationSample

import java.time.Duration

class ResultWriterTestFixtures {
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
