package org.gradle.profiler.chrometrace;

import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class ChromeTraceProfilerFactory extends ProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new ChromeTraceProfiler();
    }
}
