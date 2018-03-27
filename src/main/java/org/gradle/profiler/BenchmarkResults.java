package org.gradle.profiler;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.profiler.report.CsvGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BenchmarkResults {
    private final List<BuildScenario> allBuilds = new ArrayList<>();
    private final CsvGenerator csvGenerator;

    public BenchmarkResults(CsvGenerator csvGenerator) {
        this.csvGenerator = csvGenerator;
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
        csvGenerator.write(allBuilds);
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
        public DescriptiveStatistics getStatistics() {
            DescriptiveStatistics statistics = new DescriptiveStatistics();
            if (results.size() > scenario.getWarmUpCount() + 1) {
                for (BuildInvocationResult result : results.subList(1 + scenario.getWarmUpCount(), results.size())) {
                    statistics.addValue(result.getExecutionTime().toMillis());
                }
            }
            return statistics;
        }
    }
}
