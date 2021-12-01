package org.gradle.profiler.result;

import java.util.List;

/**
 * Provides all samples for the results.
 */
public interface SampleProvider<T extends BuildInvocationResult> {
    List<Sample<? super T>> get(List<T> results);
}
