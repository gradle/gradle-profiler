package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.gradle.profiler.report.AbstractGenerator;
import org.gradle.profiler.report.BenchmarkResult;
import org.gradle.profiler.report.BuildScenarioResult;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class BenchmarkResultCollector {
    private final List<BuildScenario> allBuilds = new ArrayList<>();
    private final List<AbstractGenerator> generators;

    public BenchmarkResultCollector(AbstractGenerator... generators) {
        this.generators = Arrays.asList(generators);
    }

    public <T extends BuildInvocationResult> Consumer<T> scenario(ScenarioDefinition scenario, List<Sample<? super T>> samples) {
        BuildScenario<T> buildScenario = new BuildScenario<>(scenario, baseLineFor(scenario), samples);
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

    private static class BuildScenario<T extends BuildInvocationResult> implements BuildScenarioResult, Consumer<T> {
        private final ScenarioDefinition scenario;
        private final BuildScenarioResult baseline;
        private final List<Sample<? super BuildInvocationResult>> samples;
        private final List<BuildInvocationResult> results = new ArrayList<>();
        private List<StatisticsImpl> statistics;

        BuildScenario(ScenarioDefinition scenario, BuildScenarioResult baseline, List<Sample<? super T>> samples) {
            this.scenario = scenario;
            this.baseline = baseline;
            this.samples = new ArrayList(samples);
        }

        @Override
        public void accept(BuildInvocationResult buildInvocationResult) {
            results.add(buildInvocationResult);
            statistics = null;
        }

        @Override
        public ScenarioDefinition getScenarioDefinition() {
            return scenario;
        }

        @Override
        public List<Sample<? super BuildInvocationResult>> getSamples() {
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
                    for (int i = 0; i < getSamples().size(); i++) {
                        Sample<? super BuildInvocationResult> sample = getSamples().get(i);
                        statistics.get(i).statistics.addValue(sample.extractFrom(result).toMillis());
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
                values[i] = getSamples().get(sample).extractFrom(buildInvocationResult).toMillis();
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
