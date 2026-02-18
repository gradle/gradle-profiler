package org.gradle.profiler.idea;

import org.gradle.profiler.gradle.GradleBuildInvocationResult;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SingleInvocationDurationSample;

import java.time.Duration;

public class IdeaBuildInvocationResult extends GradleBuildInvocationResult {

    public IdeaBuildInvocationResult(GradleBuildInvocationResult result) {
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
    public IdeaBuildActionResult getActionResult() {
        return (IdeaBuildActionResult) super.getActionResult();
    }

    public static final Sample<IdeaBuildInvocationResult> GRADLE_TOTAL_EXECUTION_TIME
        = new SingleInvocationDurationSample<>("Gradle total execution time") {
        @Override
        public Duration extractTotalDurationFrom(IdeaBuildInvocationResult result) {
            return result.getActionResult().getGradleExecutionTime();
        }
    };

    public static final Sample<IdeaBuildInvocationResult> IDE_EXECUTION_TIME
        = new SingleInvocationDurationSample<>("IDE execution time") {
        @Override
        public Duration extractTotalDurationFrom(IdeaBuildInvocationResult result) {
            return result.getActionResult().getIdeExecutionTime();
        }
    };
}
