package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.TimeSample;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

import static org.gradle.profiler.buildops.BuildOperationUtil.getSimpleBuildOperationName;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration timeToTaskExecution;
    private final Map<String, Duration> cumulativeBuildOperationTimes;
    private final String daemonPid;

    public static final Sample<GradleBuildInvocationResult> TIME_TO_TASK_EXECUTION = new TimeSample<GradleBuildInvocationResult>() {
        @Override
        public String getName() {
            return "task start";
        }

        @Override
        protected Duration getDuration(GradleBuildInvocationResult result) {
            return result.timeToTaskExecution;
        }
    };

    public static Sample<GradleBuildInvocationResult> sampleBuildOperationDuration(String buildOperationDetailsClass) {
        return new TimeSample<GradleBuildInvocationResult>() {
            @Override
            public String getName() {
                return getSimpleBuildOperationName(buildOperationDetailsClass);
            }

            @Override
            protected Duration getDuration(GradleBuildInvocationResult result) {
                return result.cumulativeBuildOperationTimes.getOrDefault(buildOperationDetailsClass, Duration.ZERO);
            }
        };
    }

    public GradleBuildInvocationResult(BuildContext buildContext, Duration executionTime, @Nullable Duration timeToTaskExecution, Map<String, Duration> cumulativeBuildOperationTimes, String daemonPid) {
        super(buildContext, executionTime);
        this.timeToTaskExecution = timeToTaskExecution;
        this.cumulativeBuildOperationTimes = cumulativeBuildOperationTimes;
        this.daemonPid = daemonPid;
    }

    public String getDaemonPid() {
        return daemonPid;
    }
}
