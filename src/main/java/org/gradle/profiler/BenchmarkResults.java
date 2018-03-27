package org.gradle.profiler;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.profiler.report.AbstractGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class BenchmarkResults {
    private final List<BuildScenario> allBuilds = new ArrayList<>();
    private final List<AbstractGenerator> generators;

    public BenchmarkResults(AbstractGenerator... generators) {
        this.generators = Arrays.asList(generators);
    }

    public Consumer<BuildInvocationResult> version(ScenarioDefinition scenario) {
        List<BuildInvocationResult> results = getResultsForVersion(scenario);
        return results::add;
    }

    private List<BuildInvocationResult> getResultsForVersion(ScenarioDefinition scenario) {
        BuildScenario buildScenario = new BuildScenario(scenario);
        allBuilds.add(buildScenario);
        return buildScenario.results;
    }

    public void write() throws IOException {
        for (AbstractGenerator generator : generators) {
            generator.write(allBuilds);
        }
    }

    private static class BuildScenario implements BuildScenarioResult {
        public final ScenarioDefinition scenario;
        public final List<BuildInvocationResult> results = new ArrayList<>();

        BuildScenario(ScenarioDefinition scenario) {
            this.scenario = scenario;
        }

        @Override
        public ScenarioDefinition getScenarioDefinition() {
            return scenario;
        }

        @Override
        public List<? extends BuildInvocationResult> getResults() {
            return results;
        }

        @Override
        public List<? extends BuildInvocationResult> getMeasuredResults() {
            if (results.size() > scenario.getWarmUpCount()) {
                return results.subList(scenario.getWarmUpCount(), results.size());
            }
            return Collections.emptyList();
        }

        @Override
        public DescriptiveStatistics getStatistics() {
            DescriptiveStatistics statistics = new DescriptiveStatistics();
            for (BuildInvocationResult result : getMeasuredResults()) {
                statistics.addValue(result.getExecutionTime().toMillis());
            }
            return statistics;
        }
    }
}
