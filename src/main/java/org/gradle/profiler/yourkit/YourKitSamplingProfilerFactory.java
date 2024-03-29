package org.gradle.profiler.yourkit;

import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class YourKitSamplingProfilerFactory extends ProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new YourKitProfiler(new YourKitConfig(false, true));
    }

    @Override
    public String getName() {
        return "yourkit";
    }
}
