package org.gradle.profiler.report;

import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.util.List;

public interface BuildScenarioResult<T extends BuildInvocationResult> {
    ScenarioDefinition getScenarioDefinition();

    /**
     * Returns the names of the samples collected for each build invocation in this scenario.
     */
    List<Sample<? super T>> getSamples();

    /**
     * Returns all results, including warm-up builds.
     */
    List<T> getResults();
}
