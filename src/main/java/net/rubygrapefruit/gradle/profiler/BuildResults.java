package net.rubygrapefruit.gradle.profiler;

import java.time.Duration;

class BuildResults {
    private final Duration executionTime;
    private final String daemonPid;

    public BuildResults(Duration executionTime, String daemonPid) {
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
