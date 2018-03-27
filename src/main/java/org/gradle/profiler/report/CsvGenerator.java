package org.gradle.profiler.report;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.profiler.BenchmarkResults;
import org.gradle.profiler.BuildInvocationResult;
import org.gradle.profiler.GradleScenarioDefinition;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class CsvGenerator {
    private final File outputFile;

    public CsvGenerator(File outputFile) {
        this.outputFile = outputFile;
    }

    public void write(List<BenchmarkResults.BuildScenario> allBuilds) throws IOException {
        int maxRows = allBuilds.stream().mapToInt(v -> v.results.size()).max().orElse(0);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("build");
            for (BenchmarkResults.BuildScenario result : allBuilds) {
                writer.write(",");
                writer.write(result.scenario.getShortDisplayName());
            }
            writer.newLine();
            writer.write("tasks");
            for (BenchmarkResults.BuildScenario result : allBuilds) {
                writer.write(",");
                if (result.scenario instanceof GradleScenarioDefinition) {
                    GradleScenarioDefinition scenario = (GradleScenarioDefinition) result.scenario;
                    writer.write(scenario.getTasks().stream().collect(Collectors.joining(" ")));
                } else {
                    writer.write("");
                }
            }
            writer.newLine();
            for (int row = 0; row < maxRows; row++) {
                for (BenchmarkResults.BuildScenario result : allBuilds) {
                    List<BuildInvocationResult> results = result.results;
                    if (row >= results.size()) {
                        continue;
                    }
                    BuildInvocationResult buildResult = results.get(row);
                    writer.write(buildResult.getDisplayName());
                    break;
                }
                for (BenchmarkResults.BuildScenario result : allBuilds) {
                    List<BuildInvocationResult> results = result.results;
                    writer.write(",");
                    if (row >= results.size()) {
                        continue;
                    }
                    BuildInvocationResult buildResult = results.get(row);
                    writer.write(String.valueOf(buildResult.getExecutionTime().toMillis()));
                }
                writer.newLine();
            }

            List<DescriptiveStatistics> statistics = allBuilds.stream().map(BenchmarkResults.BuildScenario::getStatistics).collect(Collectors.toList());
            writer.write("mean");
            for (DescriptiveStatistics statistic : statistics) {
                writer.write(",");
                writer.write(String.valueOf(statistic.getMean()));
            }
            writer.newLine();
            writer.write("median");
            for (DescriptiveStatistics statistic : statistics) {
                writer.write(",");
                writer.write(String.valueOf(statistic.getPercentile(50)));
            }
            writer.newLine();
            writer.write("stddev");
            for (DescriptiveStatistics statistic : statistics) {
                writer.write(",");
                writer.write(String.valueOf(statistic.getStandardDeviation()));
            }
            writer.newLine();
        }

    }
}
