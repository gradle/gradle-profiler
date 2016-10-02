package net.rubygrapefruit.gradle.profiler;

import java.time.Duration;

class BuildInvocationResult {
    private final Duration executionTime;
    private final String daemonPid;

    public BuildInvocationResult(Duration executionTime, String daemonPid) {
        this.executionTime = executionTime;
        this.daemonPid = daemonPid;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public String getDaemonPid() {
        return daemonPid;
    }
}
