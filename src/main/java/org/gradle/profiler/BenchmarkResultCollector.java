package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.gradle.profiler.report.AbstractGenerator;
import org.gradle.profiler.report.BenchmarkResult;
import org.gradle.profiler.report.BuildScenarioResult;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BenchmarkResultCollector {
    private final List<BuildScenario<?>> allBuilds = new ArrayList<>();
    private final List<AbstractGenerator> generators;

    public BenchmarkResultCollector(AbstractGenerator... generators) {
        this.generators = Arrays.asList(generators);
    }

    public <S extends ScenarioDefinition, T extends BuildInvocationResult> Consumer<T> scenario(S scenario, List<Sample<? super T>> samples) {
        BuildScenario<T> buildScenario = new BuildScenario<>(scenario, baseLineFor(scenario), samples);
        allBuilds.add(buildScenario);
        return buildScenario;
    }

    @SuppressWarnings("unchecked")
    private <S extends ScenarioDefinition, T extends BuildInvocationResult> BuildScenario<T> baseLineFor(S scenario) {
        if (allBuilds.isEmpty()) {
            return null;
        }
        for (BuildScenario<?> candidate : allBuilds) {
            if (candidate.getScenarioDefinition().getName().equals(scenario.getName())) {
                return (BuildScenario<T>) candidate;
            }
        }
        if (allBuilds.size() >= 2) {
            if (allBuilds.get(allBuilds.size() - 1).getScenarioDefinition().getName().equals(allBuilds.get(allBuilds.size() - 2)
                .getScenarioDefinition().getName())) {
                return null;
            }
        }
        return (BuildScenario<T>) allBuilds.get(0);
    }

    public void write() throws IOException {
        for (AbstractGenerator generator : generators) {
            generator.write(new BenchmarkResultImpl());
        }
    }

    /**
     * Summarize the results for the user.
     */
    public void summarizeResults(Consumer<String> consumer) {
        for (AbstractGenerator generator : generators) {
            generator.summarizeResults(consumer);
        }
    }

    private static class BuildScenario<T extends BuildInvocationResult> implements BuildScenarioResult<T>, Consumer<T> {
        private final ScenarioDefinition scenario;
        private final BuildScenarioResult<T> baseline;
        private final List<Sample<? super T>> samples;
        private final List<T> results = new ArrayList<>();
        private Map<Sample<? super T>, StatisticsImpl> statistics;

        BuildScenario(ScenarioDefinition scenario, BuildScenarioResult<T> baseline, List<Sample<? super T>> samples) {
            this.scenario = scenario;
            this.baseline = baseline;
            this.samples = ImmutableList.copyOf(samples);
        }

        @Override
        public void accept(T buildInvocationResult) {
            results.add(buildInvocationResult);
            statistics = null;
        }

        @Override
        public ScenarioDefinition getScenarioDefinition() {
            return scenario;
        }

        @Override
        public List<Sample<? super T>> getSamples() {
            return samples;
        }

        @Override
        public Optional<BuildScenarioResult<T>> getBaseline() {
            return Optional.ofNullable(baseline);
        }

        @Override
        public List<T> getResults() {
            return Collections.unmodifiableList(results);
        }

        @Override
        public List<T> getMeasuredResults() {
            if (results.size() > scenario.getWarmUpCount()) {
                return results.subList(scenario.getWarmUpCount(), results.size());
            }
            return Collections.emptyList();
        }

        @Override
        public Map<Sample<? super T>, ? extends Statistics> getStatistics() {
            if (statistics == null) {
                ImmutableMap.Builder<Sample<? super T>, StatisticsImpl> builder = ImmutableMap.builderWithExpectedSize(samples.size());
                for (Sample<? super T> sample : samples) {
                    double confidencePercent = getConfidencePercent(sample);
                    builder.put(sample, new StatisticsImpl(new DescriptiveStatistics(), confidencePercent));
                }
                statistics = builder.build();
                for (T result : getMeasuredResults()) {
                    for (Sample<? super T> sample : samples) {
                        statistics.get(sample).statistics.addValue(sample.extractFrom(result).toMillis());
                    }
                }
            }
            return statistics;
        }

        private double getConfidencePercent(Sample<? super T> sample) {
            if (!getBaseline().isPresent()) {
                return 0;
            }
            double[] a = toArray(getBaseline().get().getMeasuredResults(), sample);
            double[] b = toArray(getMeasuredResults(), sample);
            if (a.length == 0 || b.length == 0) {
                return 1;
            }
            return (1 - new MannWhitneyUTest().mannWhitneyUTest(a, b)) * 100;
        }

        private double[] toArray(List<T> results, Sample<? super T> sample) {
            double[] values = new double[results.size()];
            for (int i = 0; i < results.size(); i++) {
                T buildInvocationResult = results.get(i);
                values[i] = sample.extractFrom(buildInvocationResult).toMillis();
            }
            return values;
        }
    }

    private static class StatisticsImpl implements BuildScenarioResult.Statistics {
        private final DescriptiveStatistics statistics;
        private final double confidencePercent;

        StatisticsImpl(DescriptiveStatistics statistics, double confidencePercent) {
            this.statistics = statistics;
            this.confidencePercent = confidencePercent;
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
        public double getConfidencePercent() {
            return confidencePercent;
        }
    }

    private class BenchmarkResultImpl implements BenchmarkResult {
        @Override
        public List<? extends BuildScenarioResult<?>> getScenarios() {
            return allBuilds;
        }
    }
}
