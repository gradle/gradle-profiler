package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.gradle.profiler.report.AbstractGenerator;
import org.gradle.profiler.report.BenchmarkResult;
import org.gradle.profiler.report.BuildScenarioResult;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        for (BuildScenario candidate : allBuilds) {
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
        private List<StatisticsImpl> statistics;
        private List<String> samples;

        BuildScenario(ScenarioDefinition scenario, BuildScenarioResult baseline) {
            this.scenario = scenario;
            this.baseline = baseline;
        }

        @Override
        public void accept(BuildInvocationResult buildInvocationResult) {
            List<String> sampleNames = buildInvocationResult.getSamples().stream().map(BuildInvocationResult.Sample::getName).collect(Collectors.toList());
            if (results.isEmpty()) {
                samples = sampleNames;
            } else if (!samples.equals(sampleNames)) {
                throw new IllegalArgumentException("Results do not contain the same samples.");
            }
            results.add(buildInvocationResult);
            statistics = null;
        }

        @Override
        public ScenarioDefinition getScenarioDefinition() {
            return scenario;
        }

        @Override
        public List<String> getSamples() {
            return samples;
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
        public List<? extends Statistics> getStatistics() {
            if (statistics == null) {
                ImmutableList.Builder<StatisticsImpl> builder = ImmutableList.builderWithExpectedSize(samples.size());
                for (int i = 0; i < samples.size(); i++) {
                    double pvalue = getPValue(i);
                    builder.add(new StatisticsImpl(new DescriptiveStatistics(), pvalue));
                }
                statistics = builder.build();
                for (BuildInvocationResult result : getMeasuredResults()) {
                    for (int i = 0; i < result.getSamples().size(); i++) {
                        BuildInvocationResult.Sample sample = result.getSamples().get(i);
                        statistics.get(i).statistics.addValue(sample.getDuration().toMillis());
                    }
                }
            }
            return statistics;
        }

        private double getPValue(int sample) {
            if (!getBaseline().isPresent()) {
                return 0;
            }
            double[] a = toArray(getBaseline().get().getMeasuredResults(), sample);
            double[] b = toArray(getMeasuredResults(), sample);
            if (a.length == 0 || b.length == 0) {
                return 1;
            }
            return new MannWhitneyUTest().mannWhitneyUTest(a, b);
        }

        private double[] toArray(List<? extends BuildInvocationResult> results, int sample) {
            double[] values = new double[results.size()];
            for (int i = 0; i < results.size(); i++) {
                BuildInvocationResult buildInvocationResult = results.get(i);
                values[i] = buildInvocationResult.getSamples().get(sample).getDuration().toMillis();
            }
            return values;
        }
    }

    private static class StatisticsImpl implements BuildScenarioResult.Statistics {
        private final DescriptiveStatistics statistics;
        private final double pvalue;

        StatisticsImpl(DescriptiveStatistics statistics, double pvalue) {
            this.statistics = statistics;
            this.pvalue = pvalue;
        }

        @Override
        public double getMin() {
            return statistics.getMin();
        }

        @Override
        public double getMax() {
            return statistics.getMax();
        }

        @Override
        public double getMean() {
            return statistics.getMean();
        }

        @Override
        public double getMedian() {
            return statistics.getPercentile(50);
        }

        @Override
        public double getPercentile(int p) {
            return statistics.getPercentile(p);
        }

        @Override
        public double getStandardDeviation() {
            return statistics.getStandardDeviation();
        }

        @Override
        public double getPValue() {
            return pvalue;
        }
    }

    private class BenchmarkResultImpl implements BenchmarkResult {
        @Override
        public List<? extends BuildScenarioResult> getScenarios() {
            return allBuilds;
        }
    }
}
