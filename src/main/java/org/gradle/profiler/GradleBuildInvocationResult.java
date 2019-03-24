package org.gradle.profiler;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

public class GradleBuildInvocationResult extends BuildInvocationResult {
    private final Sample timeToTaskExecution;
    private final String daemonPid;

    public GradleBuildInvocationResult(String displayName, Duration executionTime, @Nullable Duration timeToTaskExecution, String daemonPid) {
        super(displayName, executionTime);
        this.timeToTaskExecution = timeToTaskExecution == null ? null : new Sample("task start", timeToTaskExecution);
        this.daemonPid = daemonPid;
    }

    public String getDaemonPid() {
        return daemonPid;
    }

    @Override
    public List<Sample> getSamples() {
        if (timeToTaskExecution == null) {
            return super.getSamples();
        }
        return ImmutableList.of(getExecutionTime(), timeToTaskExecution);
    }
}
