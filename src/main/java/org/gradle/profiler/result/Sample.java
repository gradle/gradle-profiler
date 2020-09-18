package org.gradle.profiler.result;

public interface Sample<T extends BuildInvocationResult> {
    String getName();

    String getUnit();

    double extractFrom(T result);
}
