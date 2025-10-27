package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableList;
import joptsimple.OptionSet;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.Profiler;

import java.util.List;

public class AsyncProfilerWallProfilerFactory extends AsyncProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        AsyncProfilerConfig config = super.createConfig(parsedOptions);

        // Combined cpu + wall events are not supported on macOS at this time
        // see also https://github.com/async-profiler/async-profiler/issues/740
        List<String> allEvents = OperatingSystem.isMacOS()
            ? ImmutableList.of("wall")
            : ImmutableList.of("cpu", "wall");
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
            AsyncProfilerOutputType.JFR
        );
        return new AsyncProfiler(overrides);
    }

    @Override
    public String getName() {
        return "async-profiler-wall";
    }
}
