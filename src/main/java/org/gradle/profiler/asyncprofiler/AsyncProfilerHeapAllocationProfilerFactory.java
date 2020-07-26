package org.gradle.profiler.asyncprofiler;

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
        AsyncProfilerConfig overrides = new AsyncProfilerConfig(config.getProfilerHome(), "alloc", AsyncProfilerConfig.Counter.TOTAL, 10, config.getStackDepth(), config.getFrameBuffer(), config.isIncludeSystemThreads());
        return new AsyncProfiler(overrides);
    }
}
