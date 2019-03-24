package org.gradle.profiler;

import javax.annotation.Nullable;
import java.time.Duration;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration timeToTaskExecution;
    private final String daemonPid;

    public GradleBuildInvocationResult(String displayName, Duration executionTime, Duration timeToTaskExecution, String daemonPid) {
        super(displayName, executionTime);
        this.timeToTaskExecution = timeToTaskExecution;
        this.daemonPid = daemonPid;
    }

    @Nullable
    public Duration getTimeToTaskExecution() {
        return timeToTaskExecution;
    }

    public String getDaemonPid() {
        return daemonPid;
    }
}
