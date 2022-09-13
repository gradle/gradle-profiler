package org.gradle.profiler.result;

public interface SingleInvocationSample<T extends BuildInvocationResult> extends Sample<T> {
    @Override
    default int extractTotalCountFrom(T result) {
        return 1;
    }
}
