package org.gradle.profiler.report;

import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.util.List;
import java.util.Optional;

public interface BuildScenarioResult {
    ScenarioDefinition getScenarioDefinition();

    /**
     * Returns the baseline for this scenario, if any.
     */
    Optional<BuildScenarioResult> getBaseline();

    /**
     * Returns the names of the samples collected for each build invocation in this scenario.
     */
    List<Sample<? super BuildInvocationResult>> getSamples();

    /**
     * Returns all results, including warm-up builds.
     */
    List<? extends BuildInvocationResult> getResults();

    /**
     * Returns the measured results.
     */
    List<? extends BuildInvocationResult> getMeasuredResults();

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
        double getPValue();
    }
}
