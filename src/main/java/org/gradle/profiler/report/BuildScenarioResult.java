package org.gradle.profiler.report;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.profiler.BuildInvocationResult;
import org.gradle.profiler.ScenarioDefinition;

import java.util.List;
import java.util.Optional;

public interface BuildScenarioResult {
    ScenarioDefinition getScenarioDefinition();

    /**
     * Returns the baseline for this scenario, if any.
     */
    Optional<BuildScenarioResult> getBaseline();

    /**
     * Returns the number of metrics collected for each build invocation in this scenario.
     */
    int getMetricsCount();

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

    double getPValue();
}
