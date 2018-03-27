package org.gradle.profiler;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

public interface BuildScenarioResult {
    ScenarioDefinition getScenarioDefinition();

    List<? extends BuildInvocationResult> getResults();

    DescriptiveStatistics getStatistics();
}
