package org.gradle.profiler.result;

public interface Sample<T extends BuildInvocationResult> {
    String getName();

    double extractFrom(T result);
}
