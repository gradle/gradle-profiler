package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.JvmArgsCalculator;

import java.io.File;
import java.util.List;
import java.util.Locale;

class AsyncProfilerJvmArgsCalculator implements JvmArgsCalculator {
    private final AsyncProfilerConfig profilerConfig;
    private final AsyncProfilerWorkspace workspace;
    private final boolean captureSnapshotOnProcessExit;

    AsyncProfilerJvmArgsCalculator(AsyncProfilerConfig profilerConfig, AsyncProfilerWorkspace workspace, boolean captureSnapshotOnProcessExit) {
        this.profilerConfig = profilerConfig;
        this.workspace = workspace;
        this.captureSnapshotOnProcessExit = captureSnapshotOnProcessExit;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        StringBuilder agent = new StringBuilder()
            .append("-agentpath:").append(profilerConfig.getProfilerHome()).append("/build/libasyncProfiler.so=start")
            .append(",event=").append(profilerConfig.getEvent())
            .append(",interval=").append(profilerConfig.getInterval())
            .append(",jstackdepth=").append(profilerConfig.getStackDepth())
            .append(",buffer=").append(profilerConfig.getFrameBuffer())
            .append(",collapsed=").append(profilerConfig.getCounter().name().toLowerCase(Locale.ROOT))
            .append(",ann");

        if (captureSnapshotOnProcessExit) {
            File stacks = workspace.getStacksFile();
            agent.append(",file=").append(stacks.getAbsolutePath());
        }

        jvmArgs.add(agent.toString());
    }
}
