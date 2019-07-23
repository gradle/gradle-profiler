package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration timeToTaskExecution;
    private final Map<String, Duration> cumulativeBuildOperationTimes;
    private final String daemonPid;
    public static final Sample<GradleBuildInvocationResult> TIME_TO_TASK_EXECUTION = new Sample<GradleBuildInvocationResult>() {
        @Override
        public String getName() {
            return "task start";
        }

        @Override
        public Duration extractFrom(GradleBuildInvocationResult result) {
            return result.timeToTaskExecution;
        }
    };

    public static Sample<GradleBuildInvocationResult> sampleBuildOperation(String buildOperationDetailsClass) {
        return new Sample<GradleBuildInvocationResult>() {
            @Override
            public String getName() {
                return buildOperationDetailsClass;
            }

            @Override
            public Duration extractFrom(GradleBuildInvocationResult result) {
                return result.cumulativeBuildOperationTimes.getOrDefault(buildOperationDetailsClass, Duration.ZERO);
            }
        };
    }

    public GradleBuildInvocationResult(String displayName, Duration executionTime, @Nullable Duration timeToTaskExecution, Map<String, Duration> cumulativeBuildOperationTimes, String daemonPid) {
        super(displayName, executionTime);
        this.timeToTaskExecution = timeToTaskExecution;
        this.cumulativeBuildOperationTimes = cumulativeBuildOperationTimes;
        this.daemonPid = daemonPid;
    }

    public String getDaemonPid() {
        return daemonPid;
    }

    @Nullable
    public Duration getTimeToTaskExecution() {
        return timeToTaskExecution;
    }
}
