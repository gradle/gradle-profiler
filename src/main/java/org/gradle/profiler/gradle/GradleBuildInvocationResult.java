package org.gradle.profiler.gradle;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.buildops.BuildOperationExecutionData;
import org.gradle.profiler.result.*;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

import static org.gradle.profiler.buildops.BuildOperationUtil.getSimpleBuildOperationName;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration garbageCollectionTime;
    private final Long localBuildCacheSize;
    private final Duration timeToTaskExecution;
    private final Map<String, BuildOperationExecutionData> totalBuildOperationExecutionData;
    private final String daemonPid;

    public GradleBuildInvocationResult(
        BuildContext buildContext,
        BuildActionResult actionResult,
        @Nullable Duration garbageCollectionTime,
        @Nullable Long localBuildCacheSize,
        @Nullable Duration timeToTaskExecution,
        Map<String, BuildOperationExecutionData> totalBuildOperationExecutionData,
        String daemonPid
    ) {
        super(buildContext, actionResult);
        this.garbageCollectionTime = garbageCollectionTime;
        this.localBuildCacheSize = localBuildCacheSize;
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

    public Long getLocalBuildCacheSize() {
        return localBuildCacheSize;
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

    public static final Sample<GradleBuildInvocationResult> LOCAL_BUILD_CACHE_SIZE
        = new Sample<GradleBuildInvocationResult>("local build cache size", "MiB") {
        @Override
        public double extractValue(GradleBuildInvocationResult result) {
            return result.getLocalBuildCacheSize() / 1024.0 / 1024.0;
        }

        @Override
        public int extractTotalCountFrom(GradleBuildInvocationResult result) {
            return 1;
        }
    };

    public static final Sample<GradleBuildInvocationResult> TIME_TO_TASK_EXECUTION
        = SingleInvocationDurationSample.from("task start", GradleBuildInvocationResult::getTimeToTaskExecution);
}
