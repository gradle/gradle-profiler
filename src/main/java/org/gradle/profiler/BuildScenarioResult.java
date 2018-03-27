package org.gradle.profiler;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;
import java.util.Optional;

public interface BuildScenarioResult {
    ScenarioDefinition getScenarioDefinition();

    /**
     * Returns the baseline for this scenario, if any.
     */
    Optional<BuildScenarioResult> getBaseline();

    /**
     * Returns all results, including warm-up builds.
     */
    List<? extends BuildInvocationResult> getResults();

    /**
     * Returns the measured results.
     */
    List<? extends BuildInvocationResult> getMeasuredResults();

    /**
     * Returns some statistics of the measured results.
     */
    DescriptiveStatistics getStatistics();
}
