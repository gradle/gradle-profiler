package org.gradle.profiler.heapdump;

import joptsimple.OptionSet;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

public class HeapDumpProfilerFactory extends ProfilerFactory {
    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new HeapDumpProfiler();
    }

    @Override
    public String getName() {
        return "heap-dump";
    }
}
