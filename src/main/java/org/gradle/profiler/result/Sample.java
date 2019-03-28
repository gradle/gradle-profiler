package org.gradle.profiler.result;

import java.time.Duration;

public interface Sample<T extends BuildInvocationResult> {
    String getName();

    Duration extractFrom(T result);
}
