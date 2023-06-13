package org.gradle.profiler;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.util.List;
import java.util.stream.Collectors;

class CompositeProfilerFactory extends ProfilerFactory {
    private final List<ProfilerFactory> delegates;

    CompositeProfilerFactory(final List<ProfilerFactory> delegates) {
        this.delegates = delegates;
    }

    @Override
    public String toString() {
        return delegates.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new CompositeProfiler(delegates.stream().map(profiler -> profiler.createFromOptions(parsedOptions)).collect(Collectors.toList()));
    }

    @Override
    public void addOptions(OptionParser parser) {
        throw new UnsupportedOperationException();
    }
}
