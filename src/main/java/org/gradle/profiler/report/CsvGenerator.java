package org.gradle.profiler.report;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.profiler.BuildInvocationResult;
import org.gradle.profiler.BuildScenarioResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class CsvGenerator extends AbstractGenerator {
    public CsvGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(List<? extends BuildScenarioResult> allScenarios, BufferedWriter writer) throws IOException {
        writer.write("build");
        for (BuildScenarioResult result : allScenarios) {
            writer.write(",");
            writer.write(result.getScenarioDefinition().getShortDisplayName());
        }
        writer.newLine();
        writer.write("tasks");
        for (BuildScenarioResult result : allScenarios) {
            writer.write(",");
            writer.write(result.getScenarioDefinition().getTasksDisplayName());
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
                writer.write(String.valueOf(buildResult.getExecutionTime().toMillis()));
            }
            writer.newLine();
        }

        List<DescriptiveStatistics> statistics = allScenarios.stream().map(BuildScenarioResult::getStatistics).collect(Collectors.toList());
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
