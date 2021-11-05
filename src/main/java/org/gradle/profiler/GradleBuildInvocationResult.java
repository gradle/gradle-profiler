package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

import static org.gradle.profiler.buildops.BuildOperationUtil.getSimpleBuildOperationName;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration garbageCollectionTime;
    private final Duration timeToTaskExecution;
    private final Duration gradleToolingAgentExecutionTime;
    private final Duration studioExecutionTime;
    private final Map<String, Duration> cumulativeBuildOperationTimes;
    private final String daemonPid;

    public GradleBuildInvocationResult(
        BuildContext buildContext,
        Duration executionTime,
        Duration gradleToolingAgentExecutionTime,
        Duration ideExecutionTime,
        @Nullable Duration garbageCollectionTime,
        @Nullable Duration timeToTaskExecution,
        Map<String, Duration> cumulativeBuildOperationTimes,
        String daemonPid
    ) {
        super(buildContext, executionTime);
        this.garbageCollectionTime = garbageCollectionTime;
        this.timeToTaskExecution = timeToTaskExecution;
        this.gradleToolingAgentExecutionTime = gradleToolingAgentExecutionTime;
        this.studioExecutionTime = ideExecutionTime;
        this.cumulativeBuildOperationTimes = cumulativeBuildOperationTimes;
        this.daemonPid = daemonPid;
    }

    public String getDaemonPid() {
        return daemonPid;
    }

    public static Sample<GradleBuildInvocationResult> sampleBuildOperation(String buildOperationDetailsClass) {
        return new Sample<GradleBuildInvocationResult>() {
            @Override
            public String getName() {
                return getSimpleBuildOperationName(buildOperationDetailsClass);
            }

            @Override
            public Duration extractFrom(GradleBuildInvocationResult result) {
                return result.cumulativeBuildOperationTimes.getOrDefault(buildOperationDetailsClass, Duration.ZERO);
            }
        };
    }

    public static final Sample<GradleBuildInvocationResult> GARBAGE_COLLECTION_TIME = new Sample<GradleBuildInvocationResult>() {
        @Override
        public String getName() {
            return "garbage collection time";
        }

        @Override
        public Duration extractFrom(GradleBuildInvocationResult result) {
            return result.garbageCollectionTime;
        }
    };

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

    public static final Sample<GradleBuildInvocationResult> GRADLE_TOOLING_AGENT_EXECUTION_TIME = new Sample<GradleBuildInvocationResult>() {
        @Override
        public String getName() {
            return "Gradle Tooling Agent execution time";
        }

        @Override
        public Duration extractFrom(GradleBuildInvocationResult result) {
            return result.gradleToolingAgentExecutionTime;
        }
    };

    public static final Sample<GradleBuildInvocationResult> STUDIO_EXECUTION_TIME = new Sample<GradleBuildInvocationResult>() {
        @Override
        public String getName() {
            return "Android Studio execution time";
        }

        @Override
        public Duration extractFrom(GradleBuildInvocationResult result) {
            return result.studioExecutionTime;
        }
    };

}
