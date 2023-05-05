package org.gradle.profiler.result;

import java.time.Duration;
import java.util.function.Function;

public abstract class SingleInvocationDurationSample<T extends BuildInvocationResult> extends DurationSample<T> {
    public SingleInvocationDurationSample(String name) {
        super(name);
    }

    public static <T extends BuildInvocationResult> Sample<T> from(String name, Function<T, Duration> extractor) {
        return new SingleInvocationDurationSample<T>(name) {
            @Override
            protected Duration extractTotalDurationFrom(T result) {
                return extractor.apply(result);
            }
        };
    }

    @Override
    public int extractTotalCountFrom(T result) {
        return 1;
    }
}
