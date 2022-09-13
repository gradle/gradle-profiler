package org.gradle.profiler.result;

import java.time.Duration;

public interface Sample<T extends BuildInvocationResult> {
    String getName();

    Duration extractTotalDurationFrom(T result);

    int extractTotalCountFrom(T result);
}
