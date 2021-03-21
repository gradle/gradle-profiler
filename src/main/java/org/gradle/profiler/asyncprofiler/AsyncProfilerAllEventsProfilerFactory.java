package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableList;
import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class AsyncProfilerAllEventsProfilerFactory extends ProfilerFactory {
    public static final ProfilerFactory INSTANCE = new AsyncProfilerAllEventsProfilerFactory(AsyncProfilerFactory.INSTANCE);

    private final AsyncProfilerFactory asyncProfilerFactory;

    public AsyncProfilerAllEventsProfilerFactory(AsyncProfilerFactory asyncProfilerFactory) {
        this.asyncProfilerFactory = asyncProfilerFactory;
    }

    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        AsyncProfilerConfig config = asyncProfilerFactory.createConfig(parsedOptions);
        AsyncProfilerConfig overrides = new AsyncProfilerConfig(config.getProfilerHome(), ImmutableList.of("cpu", "alloc", "lock"), AsyncProfilerConfig.Counter.SAMPLES, config.getInterval(), config.getAllocSampleSize(), config.getLockThreshold(), config.getStackDepth(), config.isIncludeSystemThreads());
        return new AsyncProfiler(overrides);
    }
}
