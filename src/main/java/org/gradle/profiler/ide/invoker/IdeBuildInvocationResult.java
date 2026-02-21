package org.gradle.profiler.ide.invoker;

import org.gradle.profiler.gradle.GradleBuildInvocationResult;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SingleInvocationDurationSample;

import java.time.Duration;
import java.util.List;

public class IdeBuildInvocationResult extends GradleBuildInvocationResult {

    public IdeBuildInvocationResult(GradleBuildInvocationResult result) {
        super(
            result.getBuildContext(),
            result.getActionResult(),
            result.getGarbageCollectionTime(),
            result.getLocalBuildCacheSize(),
            result.getTimeToTaskExecution(),
            result.getBuildOperationExecutionData(),
            result.getDaemonPid()
        );
    }

    @Override
    public IdeBuildActionResult getActionResult() {
        return (IdeBuildActionResult) super.getActionResult();
    }

    public static final Sample<IdeBuildInvocationResult> GRADLE_TOTAL_EXECUTION_TIME
        = new SingleInvocationDurationSample<IdeBuildInvocationResult>("Gradle total execution time") {
        @Override
        public Duration extractTotalDurationFrom(IdeBuildInvocationResult result) {
            return result.getActionResult().getGradleTotalExecutionTime();
        }
    };

    public static Sample<IdeBuildInvocationResult> getGradleToolingAgentExecutionTime(int index) {
        return new SingleInvocationDurationSample<IdeBuildInvocationResult>("Gradle execution time #" + (index + 1)) {
            @Override
            protected Duration extractTotalDurationFrom(IdeBuildInvocationResult result) {
                List<Duration> executionTimes = result.getActionResult().getGradleExecutionTimes();
                return index >= executionTimes.size()
                    ? Duration.ZERO
                    : executionTimes.get(index);
            }
        };
    }

    public static final Sample<IdeBuildInvocationResult> IDE_EXECUTION_TIME
        = new SingleInvocationDurationSample<IdeBuildInvocationResult>("IDE execution time") {
        @Override
        public Duration extractTotalDurationFrom(IdeBuildInvocationResult result) {
            return result.getActionResult().getIdeExecutionTime();
        }
    };
}
