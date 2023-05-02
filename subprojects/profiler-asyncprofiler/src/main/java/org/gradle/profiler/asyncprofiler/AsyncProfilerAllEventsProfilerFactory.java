package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableList;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class AsyncProfilerAllEventsProfilerFactory extends AsyncProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        AsyncProfilerConfig config = super.createConfig(parsedOptions);
        AsyncProfilerConfig overrides = new AsyncProfilerConfig(config.getProfilerHome(), ImmutableList.of("cpu", "alloc", "lock"), AsyncProfilerConfig.Counter.SAMPLES, config.getInterval(), config.getAllocSampleSize(), config.getLockThreshold(), config.getStackDepth(), config.isIncludeSystemThreads());
        return new AsyncProfiler(overrides);
    }

    @Override
    public String getName() {
        return "async-profiler-all";
    }
}
