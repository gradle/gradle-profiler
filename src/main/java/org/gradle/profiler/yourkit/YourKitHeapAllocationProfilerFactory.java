package org.gradle.profiler.yourkit;

import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class YourKitHeapAllocationProfilerFactory extends ProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new YourKitProfiler(new YourKitConfig(true, false));
    }
}
