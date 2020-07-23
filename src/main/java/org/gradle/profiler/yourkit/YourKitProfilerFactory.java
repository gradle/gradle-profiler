package org.gradle.profiler.yourkit;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class YourKitProfilerFactory extends ProfilerFactory {
    private OptionSpecBuilder memory;
    private OptionSpecBuilder sampling;

    @Override
    public void addOptions(OptionParser parser) {
        memory = parser.accepts("yourkit-memory", "Perform memory profiling instead of CPU profiling");
        sampling = parser.accepts("yourkit-sampling", "Use sampling instead of tracing for CPU profiling");
    }

    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new YourKitProfiler(newConfigObject(parsedOptions));
    }

    private YourKitConfig newConfigObject(OptionSet parsedOptions) {
        YourKitConfig yourKitConfig = new YourKitConfig(parsedOptions.has(memory), parsedOptions.has(sampling));
        if (yourKitConfig.isMemorySnapshot() && yourKitConfig.isUseSampling()) {
            throw new IllegalArgumentException("Cannot use memory profiling and CPU sampling at the same time.");
        }
        return yourKitConfig;
    }
}
