package org.gradle.profiler.asyncprofiler;

import java.io.File;

public class AsyncProfilerConfig {
    private final File profilerHome;
    private final String event;
    private final Counter counter;
    private final int interval;
    private final int stackDepth;
    private final int frameBuffer;
    private final boolean includeSystemThreads;

    public AsyncProfilerConfig(File profilerHome, String event, Counter counter, int interval, int stackDepth, int frameBuffer, boolean includeSystemThreads) {
        this.profilerHome = profilerHome;
        this.event = event;
        this.counter = counter;
        this.interval = interval;
        this.stackDepth = stackDepth;
        this.frameBuffer = frameBuffer;
        this.includeSystemThreads = includeSystemThreads;
    }

    public File getProfilerHome() {
        return profilerHome;
    }

    public String getEvent() {
        return event;
    }

    public Counter getCounter() {
        return counter;
    }

    public int getInterval() {
        return interval;
    }

    public int getStackDepth() {
        return stackDepth;
    }

    public int getFrameBuffer() {
        return frameBuffer;
    }

    public boolean isIncludeSystemThreads() {
        return includeSystemThreads;
    }

    public enum Counter {
        SAMPLES,
        TOTAL
    }
}
