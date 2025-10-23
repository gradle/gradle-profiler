package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableList;
import joptsimple.OptionSet;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.Profiler;

public class AsyncProfilerAllEventsProfilerFactory extends AsyncProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        // TODO support all event from 4.1
        AsyncProfilerConfig config = super.createConfig(parsedOptions);

        ImmutableList.Builder<String> allEvents = ImmutableList.<String>builder().add("cpu", "alloc", "lock");
        // Combined cpu + wall is only supported on Linux at this time
        if (!OperatingSystem.isMacOS()) {
            allEvents.add("wall");
        }
        AsyncProfilerConfig overrides = new AsyncProfilerConfig(
            config.getDistribution(),
            allEvents.build(),
            AsyncProfilerConfig.Counter.SAMPLES,
            config.getInterval(),
            config.getAllocSampleSize(),
            config.getLockThreshold(),
            config.getWallInterval(),
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
