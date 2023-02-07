package org.gradle.profiler;

import org.gradle.profiler.BuildAction.BuildActionResult;
import org.gradle.profiler.buildops.BuildOperationExecutionData;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.DurationSample;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SingleInvocationDurationSample;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

import static org.gradle.profiler.buildops.BuildOperationUtil.getSimpleBuildOperationName;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration garbageCollectionTime;
    private final Long localCacheSize;
    private final Duration timeToTaskExecution;
    private final Map<String, BuildOperationExecutionData> totalBuildOperationExecutionData;
    private final String daemonPid;

    public GradleBuildInvocationResult(
        BuildContext buildContext,
        BuildActionResult actionResult,
        @Nullable Duration garbageCollectionTime,
        @Nullable Long localCacheSize,
        @Nullable Duration timeToTaskExecution,
        Map<String, BuildOperationExecutionData> totalBuildOperationExecutionData,
        String daemonPid
    ) {
        super(buildContext, actionResult);
        this.garbageCollectionTime = garbageCollectionTime;
        this.localCacheSize = localCacheSize;
        this.timeToTaskExecution = timeToTaskExecution;
        this.totalBuildOperationExecutionData = totalBuildOperationExecutionData;
        this.daemonPid = daemonPid;
    }

    public String getDaemonPid() {
        return daemonPid;
    }

    public Duration getGarbageCollectionTime() {
        return garbageCollectionTime;
    }

    public Long getLocalCacheSize() {
        return localCacheSize;
    }

    public Duration getTimeToTaskExecution() {
        return timeToTaskExecution;
    }

    public Map<String, BuildOperationExecutionData> getTotalBuildOperationExecutionData() {
        return totalBuildOperationExecutionData;
    }

    public static Sample<GradleBuildInvocationResult> sampleBuildOperation(String buildOperationDetailsClass) {
        return new DurationSample<GradleBuildInvocationResult>(getSimpleBuildOperationName(buildOperationDetailsClass)) {
            @Override
            protected Duration extractTotalDurationFrom(GradleBuildInvocationResult result) {
                return Duration.ofMillis(getExecutionData(result).getValue());
            }

            @Override
            public int extractTotalCountFrom(GradleBuildInvocationResult result) {
                return getExecutionData(result).getTotalCount();
            }

            private BuildOperationExecutionData getExecutionData(GradleBuildInvocationResult result) {
                return result.totalBuildOperationExecutionData.getOrDefault(buildOperationDetailsClass, BuildOperationExecutionData.ZERO);
            }
        };
    }

    public static final Sample<GradleBuildInvocationResult> GARBAGE_COLLECTION_TIME
        = SingleInvocationDurationSample.from("garbage collection time", GradleBuildInvocationResult::getGarbageCollectionTime);

    public static final Sample<GradleBuildInvocationResult> LOCAL_CACHE_SIZE
        = new Sample<GradleBuildInvocationResult>("local cache size", "MiB") {
        @Override
        public double extractValue(GradleBuildInvocationResult result) {
            return result.getLocalCacheSize() / 1024.0 / 1024.0;
        }

        @Override
        public int extractTotalCountFrom(GradleBuildInvocationResult result) {
            return 1;
        }
    };

    public static final Sample<GradleBuildInvocationResult> TIME_TO_TASK_EXECUTION
        = SingleInvocationDurationSample.from("task start", GradleBuildInvocationResult::getTimeToTaskExecution);
}
