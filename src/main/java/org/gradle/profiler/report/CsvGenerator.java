package org.gradle.profiler.report;

import org.gradle.profiler.BuildInvocationResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CsvGenerator extends AbstractGenerator {
    public CsvGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(BenchmarkResult benchmarkResult, BufferedWriter writer) throws IOException {
        List<? extends BuildScenarioResult> allScenarios = benchmarkResult.getScenarios();
        writer.write("scenario");
        for (BuildScenarioResult result : allScenarios) {
            for (int i = 0; i < result.getSamples().size(); i++) {
                writer.write(",");
                writer.write(result.getScenarioDefinition().getName());
            }
        }
        writer.newLine();

        writer.write("version");
        for (BuildScenarioResult result : allScenarios) {
            for (int i = 0; i < result.getSamples().size(); i++) {
                writer.write(",");
                writer.write(result.getScenarioDefinition().getBuildToolDisplayName());
            }
        }
        writer.newLine();

        writer.write("tasks");
        for (BuildScenarioResult result : allScenarios) {
            for (int i = 0; i < result.getSamples().size(); i++) {
                writer.write(",");
                writer.write(result.getScenarioDefinition().getTasksDisplayName());
            }
        }
        writer.newLine();

        writer.write("value");
        for (BuildScenarioResult result : allScenarios) {
            for (String sample : result.getSamples()) {
                writer.write(",");
                writer.write(sample);
            }
        }
        writer.newLine();

        int maxRows = allScenarios.stream().mapToInt(v -> v.getResults().size()).max().orElse(0);
        for (int row = 0; row < maxRows; row++) {
            for (BuildScenarioResult result : allScenarios) {
                List<? extends BuildInvocationResult> results = result.getResults();
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                writer.write(buildResult.getDisplayName());
                break;
            }
            for (BuildScenarioResult result : allScenarios) {
                List<? extends BuildInvocationResult> results = result.getResults();
                writer.write(",");
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                for (int i = 0; i < buildResult.getSamples().size(); i++) {
                    Duration duration = buildResult.getSamples().get(i).getDuration();
                    if (i > 0) {
                        writer.write(",");
                    }
                    writer.write(String.valueOf(duration.toMillis()));
                }
            }
            writer.newLine();
        }

        List<BuildScenarioResult.Statistics> statistics = allScenarios.stream().flatMap(s -> s.getStatistics().stream()).collect(Collectors.toList());
        statistic(writer, "mean", statistics, BuildScenarioResult.Statistics::getMean);
        statistic(writer, "min", statistics, BuildScenarioResult.Statistics::getMin);
        statistic(writer, "25th percentile", statistics, v -> v.getPercentile(25));
        statistic(writer, "median", statistics, v -> v.getMedian());
        statistic(writer, "75th percentile", statistics, v -> v.getPercentile(75));
        statistic(writer, "max", statistics, BuildScenarioResult.Statistics::getMax);
        statistic(writer, "stddev", statistics, BuildScenarioResult.Statistics::getStandardDeviation);
    }

    private void statistic(BufferedWriter writer, String name, List<BuildScenarioResult.Statistics> statistics, Function<BuildScenarioResult.Statistics, Double> value) throws IOException {
        writer.write(name);
        for (BuildScenarioResult.Statistics statistic : statistics) {
            writer.write(",");
            writer.write(String.valueOf(value.apply(statistic)));
        }
        writer.newLine();
    }
}
