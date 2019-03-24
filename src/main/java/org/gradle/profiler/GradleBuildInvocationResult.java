package org.gradle.profiler;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Duration timeToTaskExecution;
    private final String daemonPid;

    public GradleBuildInvocationResult(String displayName, Duration executionTime, @Nullable Duration timeToTaskExecution, String daemonPid) {
        super(displayName, executionTime);
        this.timeToTaskExecution = timeToTaskExecution;
        this.daemonPid = daemonPid;
    }

    public String getDaemonPid() {
        return daemonPid;
    }

    @Override
    public List<Duration> getMetrics() {
        if (timeToTaskExecution == null) {
            return super.getMetrics();
        }
        return ImmutableList.of(getExecutionTime(), timeToTaskExecution);
    }
}
