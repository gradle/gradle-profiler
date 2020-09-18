package org.gradle.profiler.result;

import java.time.Duration;

public abstract class TimeSample<T extends BuildInvocationResult> implements Sample<T> {

    @Override
    public String getUnit() {
        return "ms";
    }

    @Override
    public double extractFrom(T result) {
        return getDuration(result).toNanos() / 1000000d;
    }

    abstract protected Duration getDuration(T result);
}
