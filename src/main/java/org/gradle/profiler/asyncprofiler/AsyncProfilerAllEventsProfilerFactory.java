package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableList;
import joptsimple.OptionSet;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.Profiler;

import java.util.List;

public class AsyncProfilerAllEventsProfilerFactory extends AsyncProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        // TODO support all event from 4.1
        AsyncProfilerConfig config = super.createConfig(parsedOptions);

        // Combined cpu + wall events are not supported on macOS at this time
        List<String> allEvents = OperatingSystem.isMacOS()
            ? ImmutableList.of("cpu", "alloc", "lock")
            : ImmutableList.of("cpu", "wall", "alloc", "lock");
        AsyncProfilerConfig overrides = new AsyncProfilerConfig(
            config.getDistribution(),
            allEvents,
            AsyncProfilerConfig.Counter.SAMPLES,
            config.getInterval(),
            config.getAllocSampleSize(),
            config.getLockThreshold(),
            config.getWallInterval(),
            config.getStackDepth(),
            config.isIncludeSystemThreads(),
            config.getPreferredOutputType()
        );
        return new AsyncProfiler(overrides);
    }

    @Override
    public String getName() {
        return "async-profiler-all";
    }
}
