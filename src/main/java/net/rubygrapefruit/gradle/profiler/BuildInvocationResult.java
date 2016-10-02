package net.rubygrapefruit.gradle.profiler;

import java.time.Duration;

class BuildInvocationResult {
    private final String displayName;
    private final Duration executionTime;
    private final String daemonPid;

    public BuildInvocationResult(String displayName, Duration executionTime, String daemonPid) {
        this.displayName = displayName;
        this.executionTime = executionTime;
        this.daemonPid = daemonPid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public String getDaemonPid() {
        return daemonPid;
    }
}
