package org.gradle.profiler.profilers.support;

/**
 * Sent by a build process when the build has finished, before the build result is returned to the Gradle client.
 */
public final class BuildFinished implements ProfilerMessage {
    private final String pid;

    public BuildFinished(String pid) {
        this.pid = pid;
    }

    /**
     * The PID of the build process.
     */
    public String getPid() {
        return pid;
    }

    @Override
    public String toString() {
        return "BuildFinished(pid=" + pid + ")";
    }
}
