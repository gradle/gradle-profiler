package org.gradle.profiler.report;

import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.util.List;
import java.util.Optional;

public interface BuildScenarioResult<T extends BuildInvocationResult> {
    ScenarioDefinition getScenarioDefinition();

    /**
     * Returns the baseline for this scenario, if any.
     */
    Optional<BuildScenarioResult<T>> getBaseline();

    /**
     * Returns the names of the samples collected for each build invocation in this scenario.
     */
    List<Sample<? super T>> getSamples();

    /**
     * Returns all results, including warm-up builds.
     */
    List< T> getResults();

    /**
     * Returns the measured results.
     */
    List<T> getMeasuredResults();

    /**
     * Returns some statistics of each sample.
     */
    List<? extends Statistics> getStatistics();

    interface Statistics {
        double getMin();

        double getMax();

        double getMean();

        double getMedian();

        double getPercentile(int p);

        double getStandardDeviation();

        // Relative to base-line
        double getConfidencePercent();
    }
}
