package org.gradle.profiler;

import org.gradle.profiler.BuildAction.BuildActionResult;
import org.gradle.profiler.buildops.BuildOperationDuration;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.DurationOnlySample;
import org.gradle.profiler.result.Sample;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

import static org.gradle.profiler.buildops.BuildOperationUtil.getSimpleBuildOperationName;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration garbageCollectionTime;
    private final Duration timeToTaskExecution;
    private final Map<String, BuildOperationDuration> cumulativeBuildOperationDurations;
    private final String daemonPid;

    public GradleBuildInvocationResult(
        BuildContext buildContext,
        BuildActionResult actionResult,
        @Nullable Duration garbageCollectionTime,
        @Nullable Duration timeToTaskExecution,
        Map<String, BuildOperationDuration> cumulativeBuildOperationDurations,
        String daemonPid
    ) {
        super(buildContext, actionResult);
        this.garbageCollectionTime = garbageCollectionTime;
        this.timeToTaskExecution = timeToTaskExecution;
        this.cumulativeBuildOperationDurations = cumulativeBuildOperationDurations;
        this.daemonPid = daemonPid;
    }

    public String getDaemonPid() {
        return daemonPid;
    }

    public Duration getGarbageCollectionTime() {
        return garbageCollectionTime;
    }

    public Duration getTimeToTaskExecution() {
        return timeToTaskExecution;
    }

    public Map<String, BuildOperationDuration> getCumulativeBuildOperationDurations() {
        return cumulativeBuildOperationDurations;
    }

    public static Sample<GradleBuildInvocationResult> sampleBuildOperation(String buildOperationDetailsClass) {
        return new Sample<GradleBuildInvocationResult>() {
            @Override
            public String getName() {
                return getSimpleBuildOperationName(buildOperationDetailsClass);
            }

            @Override
            public Duration extractTotalDurationFrom(GradleBuildInvocationResult result) {
                BuildOperationDuration duration = result.cumulativeBuildOperationDurations.get(buildOperationDetailsClass);
                return duration == null ? Duration.ZERO : duration.getTotalDuration();
            }

            @Override
            public int extractTotalCountFrom(GradleBuildInvocationResult result) {
                BuildOperationDuration duration = result.cumulativeBuildOperationDurations.get(buildOperationDetailsClass);
                return duration == null ? 0 : duration.getTotalCount();
            }
        };
    }

    public static final Sample<GradleBuildInvocationResult> GARBAGE_COLLECTION_TIME = new DurationOnlySample<GradleBuildInvocationResult>() {
        @Override
        public String getName() {
            return "garbage collection time";
        }

        @Override
        public Duration extractTotalDurationFrom(GradleBuildInvocationResult result) {
            return result.garbageCollectionTime;
        }
    };

    public static final Sample<GradleBuildInvocationResult> TIME_TO_TASK_EXECUTION = new DurationOnlySample<GradleBuildInvocationResult>() {
        @Override
        public String getName() {
            return "task start";
        }

        @Override
        public Duration extractTotalDurationFrom(GradleBuildInvocationResult result) {
            return result.timeToTaskExecution;
        }
    };
}
