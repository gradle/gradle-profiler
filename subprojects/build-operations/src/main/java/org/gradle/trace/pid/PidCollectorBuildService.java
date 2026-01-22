package org.gradle.trace.pid;

public abstract class PidCollectorBuildService extends AbstractPidCollectorBuildService {

    @Override
    protected Long getPid() {
        return ProcessHandle.current().pid();
    }
}
