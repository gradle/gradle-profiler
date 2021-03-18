package org.gradle.profiler.asyncprofiler;

import com.google.common.base.Joiner;

import java.io.File;
import java.util.List;

public class AsyncProfilerConfig {
    private final File profilerHome;
    private final List<String> events;
    private final Counter counter;
    private final int interval;
    private final int stackDepth;
    private final boolean includeSystemThreads;

    public AsyncProfilerConfig(File profilerHome, List<String> events, Counter counter, int interval, int stackDepth, boolean includeSystemThreads) {
        this.profilerHome = profilerHome;
        this.events = events;
        this.counter = counter;
        this.interval = interval;
        this.stackDepth = stackDepth;
        this.includeSystemThreads = includeSystemThreads;
    }

    public File getProfilerHome() {
        return profilerHome;
    }

    public String getJoinedEvents() {
        return Joiner.on(",").join(events);
    }

    public List<String> getEvents() {
        return events;
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

    public boolean isIncludeSystemThreads() {
        return includeSystemThreads;
    }

    public enum Counter {
        SAMPLES,
        TOTAL
    }
}
