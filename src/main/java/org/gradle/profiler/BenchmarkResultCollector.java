package org.gradle.profiler;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.gradle.profiler.report.AbstractGenerator;
import org.gradle.profiler.report.BenchmarkResult;
import org.gradle.profiler.report.BuildScenarioResult;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class BenchmarkResultCollector {
    private final List<BuildScenario> allBuilds = new ArrayList<>();
    private final List<AbstractGenerator> generators;

    public BenchmarkResultCollector(AbstractGenerator... generators) {
        this.generators = Arrays.asList(generators);
    }

    public Consumer<BuildInvocationResult> version(ScenarioDefinition scenario) {
        return getResultsForScenario(scenario);
    }

    private BuildScenario getResultsForScenario(ScenarioDefinition scenario) {
        BuildScenario buildScenario = new BuildScenario(scenario, baseLineFor(scenario));
        allBuilds.add(buildScenario);
        return buildScenario;
    }

    private BuildScenario baseLineFor(ScenarioDefinition scenario) {
        if (allBuilds.isEmpty()) {
            return null;
        }
        for (int i = 0; i < allBuilds.size(); i++) {
            BuildScenario candidate = allBuilds.get(i);
            if (candidate.getScenarioDefinition().getName().equals(scenario.getName())) {
                return candidate;
            }
        }
        if (allBuilds.size() >= 2) {
            if (allBuilds.get(allBuilds.size() - 1).getScenarioDefinition().getName().equals(allBuilds.get(allBuilds.size() - 2)
                .getScenarioDefinition().getName())) {
                return null;
            }
        }
        return allBuilds.get(0);
    }

    public void write() throws IOException {
        for (AbstractGenerator generator : generators) {
            generator.write(new BenchmarkResultImpl());
        }
    }

    private static class BuildScenario implements BuildScenarioResult, Consumer<BuildInvocationResult> {
        private final ScenarioDefinition scenario;
        private final BuildScenarioResult baseline;
        private final List<BuildInvocationResult> results = new ArrayList<>();
        private DescriptiveStatistics statistics;
        private int metricsCount;

        BuildScenario(ScenarioDefinition scenario, BuildScenarioResult baseline) {
            this.scenario = scenario;
            this.baseline = baseline;
        }

        @Override
        public void accept(BuildInvocationResult buildInvocationResult) {
            if (results.isEmpty()) {
                metricsCount = buildInvocationResult.getMetrics().size();
            } else if (buildInvocationResult.getMetrics().size() != metricsCount) {
                throw new IllegalArgumentException("Results do not contain the same number of metrics.");
            }
            results.add(buildInvocationResult);
            statistics = null;
        }

        @Override
        public ScenarioDefinition getScenarioDefinition() {
            return scenario;
        }

        @Override
        public int getMetricsCount() {
            return metricsCount;
        }

        @Override
        public Optional<BuildScenarioResult> getBaseline() {
            return Optional.ofNullable(baseline);
        }

        @Override
        public List<? extends BuildInvocationResult> getResults() {
            return Collections.unmodifiableList(results);
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
            if (statistics == null) {
                statistics = new DescriptiveStatistics();
                for (BuildInvocationResult result : getMeasuredResults()) {
                    statistics.addValue(result.getExecutionTime().toMillis());
                }
            }
            return statistics;
        }

        @Override
        public double getPValue() {
            double[] a = toArray(getBaseline().get().getMeasuredResults());
            double[] b = toArray(getMeasuredResults());
            if (a.length == 0 || b.length == 0) {
                return 1;
            }
            return new MannWhitneyUTest().mannWhitneyUTest(a, b);
        }

        private double[] toArray(List<? extends BuildInvocationResult> results) {
            double[] values = new double[results.size()];
            for (int i = 0; i < results.size(); i++) {
                BuildInvocationResult buildInvocationResult = results.get(i);
                values[i] = buildInvocationResult.getExecutionTime().toMillis();
            }
            return values;
        }
    }

    private class BenchmarkResultImpl implements BenchmarkResult {
        @Override
        public List<? extends BuildScenarioResult> getScenarios() {
            return allBuilds;
        }
    }
}
