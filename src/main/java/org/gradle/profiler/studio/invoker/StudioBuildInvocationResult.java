package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.GradleBuildInvocationResult;
import org.gradle.profiler.result.SingleInvocationSample;
import org.gradle.profiler.result.Sample;

import java.time.Duration;
import java.util.List;

public class StudioBuildInvocationResult extends GradleBuildInvocationResult {

    public StudioBuildInvocationResult(GradleBuildInvocationResult result) {
        super(
            result.getBuildContext(),
            result.getActionResult(),
            result.getGarbageCollectionTime(),
            result.getTimeToTaskExecution(),
            result.getTotalBuildOperationExecutionData(),
            result.getDaemonPid()
        );
    }

    @Override
    public StudioBuildActionResult getActionResult() {
        return (StudioBuildActionResult) super.getActionResult();
    }

    public static final Sample<StudioBuildInvocationResult> GRADLE_TOTAL_EXECUTION_TIME = new SingleInvocationSample<StudioBuildInvocationResult>() {
        @Override
        public String getName() {
            return "Gradle total execution time";
        }

        @Override
        public Duration extractTotalDurationFrom(StudioBuildInvocationResult result) {
            return result.getActionResult().getGradleTotalExecutionTime();
        }
    };

    public static Sample<StudioBuildInvocationResult> getGradleToolingAgentExecutionTime(int index) {
        return new SingleInvocationSample<StudioBuildInvocationResult>() {
            @Override
            public String getName() {
                return "Gradle execution time #" + (index + 1);
            }

            @Override
            public Duration extractTotalDurationFrom(StudioBuildInvocationResult result) {
                List<Duration> executionTimes = result.getActionResult().getGradleExecutionTimes();
                return index >= executionTimes.size()
                    ? Duration.ZERO
                    : executionTimes.get(index);
            }
        };
    }

    public static final Sample<StudioBuildInvocationResult> IDE_EXECUTION_TIME = new SingleInvocationSample<StudioBuildInvocationResult>() {
        @Override
        public String getName() {
            return "IDE execution time";
        }

        @Override
        public Duration extractTotalDurationFrom(StudioBuildInvocationResult result) {
            return result.getActionResult().getIdeExecutionTime();
        }
    };
}
