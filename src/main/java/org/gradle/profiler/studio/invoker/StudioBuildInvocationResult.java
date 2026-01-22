package org.gradle.profiler.studio.invoker;

import org.gradle.profiler.gradle.GradleBuildInvocationResult;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SingleInvocationDurationSample;

import java.time.Duration;
import java.util.List;

public class StudioBuildInvocationResult extends GradleBuildInvocationResult {

    public StudioBuildInvocationResult(GradleBuildInvocationResult result) {
        super(
            result.getBuildContext(),
            result.getActionResult(),
            result.getGarbageCollectionTime(),
            result.getLocalBuildCacheSize(),
            result.getTimeToTaskExecution(),
            result.getTotalBuildOperationExecutionData(),
            result.getDaemonPid()
        );
    }

    @Override
    public StudioBuildActionResult getActionResult() {
        return (StudioBuildActionResult) super.getActionResult();
    }

    public static final Sample<StudioBuildInvocationResult> GRADLE_TOTAL_EXECUTION_TIME
        = new SingleInvocationDurationSample<StudioBuildInvocationResult>("Gradle total execution time") {
        @Override
        public Duration extractTotalDurationFrom(StudioBuildInvocationResult result) {
            return result.getActionResult().getGradleTotalExecutionTime();
        }
    };

    public static Sample<StudioBuildInvocationResult> getGradleToolingAgentExecutionTime(int index) {
        return new SingleInvocationDurationSample<StudioBuildInvocationResult>("Gradle execution time #" + (index + 1)) {
            @Override
            protected Duration extractTotalDurationFrom(StudioBuildInvocationResult result) {
                List<Duration> executionTimes = result.getActionResult().getGradleExecutionTimes();
                return index >= executionTimes.size()
                    ? Duration.ZERO
                    : executionTimes.get(index);
            }
        };
    }

    public static final Sample<StudioBuildInvocationResult> IDE_EXECUTION_TIME
        = new SingleInvocationDurationSample<StudioBuildInvocationResult>("IDE execution time") {
        @Override
        public Duration extractTotalDurationFrom(StudioBuildInvocationResult result) {
            return result.getActionResult().getIdeExecutionTime();
        }
    };
}
