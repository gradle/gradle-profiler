package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableList;
import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class AsyncProfilerHeapAllocationProfilerFactory extends ProfilerFactory {
    public static final ProfilerFactory INSTANCE = new AsyncProfilerHeapAllocationProfilerFactory(AsyncProfilerFactory.INSTANCE);

    private final AsyncProfilerFactory asyncProfilerFactory;

    public AsyncProfilerHeapAllocationProfilerFactory(AsyncProfilerFactory asyncProfilerFactory) {
        this.asyncProfilerFactory = asyncProfilerFactory;
    }

    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        AsyncProfilerConfig config = asyncProfilerFactory.createConfig(parsedOptions);
        AsyncProfilerConfig overrides = new AsyncProfilerConfig(config.getProfilerHome(), ImmutableList.of("alloc"), AsyncProfilerConfig.Counter.TOTAL, config.getInterval(), config.getAllocSampleSize(), config.getLockThreshold(), config.getStackDepth(), config.isIncludeSystemThreads());
        return new AsyncProfiler(overrides);
    }
}
