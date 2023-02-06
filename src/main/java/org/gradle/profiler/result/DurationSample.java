package org.gradle.profiler.result;

import java.time.Duration;

public abstract class DurationSample<T extends BuildInvocationResult> extends Sample<T> {
    public DurationSample(String name) {
        super(name, "ms");
    }

    @Override
    public double extractValue(T result) {
        return extractTotalDurationFrom(result).toNanos() / 1_000_000d;
    }

    protected abstract Duration extractTotalDurationFrom(T result);
}
