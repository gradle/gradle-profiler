package org.gradle.profiler.asyncprofiler;

import com.google.common.base.Joiner;

import java.util.List;

public class AsyncProfilerConfig {
    public static final String EVENT_ALLOC = "alloc";
    public static final String EVENT_LOCK = "lock";
    public static final String EVENT_WALL = "wall";

    private final AsyncProfilerDistribution asyncProfilerDistribution;
    private final List<String> events;
    private final Counter counter;
    private final int interval;
    private final int allocSampleSize;
    private final int lockThreshold;
    private final int wallInterval;
    private final int stackDepth;
    private final boolean includeSystemThreads;
    private final AsyncProfilerOutputType preferredOutputType;

    public AsyncProfilerConfig(
        AsyncProfilerDistribution asyncProfilerDistribution,
        List<String> events,
        Counter counter,
        int interval,
        int allocSampleSize,
        int lockThreshold,
        int wallInterval,
        int stackDepth,
        boolean includeSystemThreads,
        AsyncProfilerOutputType preferredOutputType
    ) {
        this.asyncProfilerDistribution = asyncProfilerDistribution;
        this.events = events;
        this.counter = counter;
        this.interval = interval;
        this.allocSampleSize = allocSampleSize;
        this.lockThreshold = lockThreshold;
        this.wallInterval = wallInterval;
        this.stackDepth = stackDepth;
        this.includeSystemThreads = includeSystemThreads;
        this.preferredOutputType = preferredOutputType;
    }

    public AsyncProfilerDistribution getDistribution() {
        return asyncProfilerDistribution;
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

    public int getAllocSampleSize() {
        return allocSampleSize;
    }

    public int getLockThreshold() {
        return lockThreshold;
    }

    public int getWallInterval() {
        return wallInterval;
    }

    public int getStackDepth() {
        return stackDepth;
    }

    public boolean isIncludeSystemThreads() {
        return includeSystemThreads;
    }

    public AsyncProfilerOutputType getPreferredOutputType() {
        return preferredOutputType;
    }

    public enum Counter {
        SAMPLES,
        TOTAL
    }
}
