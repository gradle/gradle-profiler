package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableList;
import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;

public class AsyncProfilerAllEventsProfilerFactory extends AsyncProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        // TODO support all event from 4.1
        AsyncProfilerConfig config = super.createConfig(parsedOptions);
        AsyncProfilerConfig overrides = new AsyncProfilerConfig(
            config.getDistribution(),
            ImmutableList.of("cpu", "alloc", "lock"),
            AsyncProfilerConfig.Counter.SAMPLES,
            config.getInterval(),
            config.getAllocSampleSize(),
            config.getLockThreshold(),
            config.getStackDepth(),
            config.isIncludeSystemThreads()
        );
        return new AsyncProfiler(overrides);
    }

    @Override
    public String getName() {
        return "async-profiler-all";
    }
}
