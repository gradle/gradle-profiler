package org.gradle.profiler;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BenchmarkResults {
    private final List<BuildScenario> allBuilds = new ArrayList<>();

    public Consumer<BuildInvocationResult> version(ScenarioDefinition scenario, GradleVersion version) {
        List<BuildInvocationResult> results = getResultsForVersion(scenario, version);
        return buildInvocationResult -> results.add(buildInvocationResult);
    }

    private List<BuildInvocationResult> getResultsForVersion(ScenarioDefinition scenario, GradleVersion version) {
        BuildScenario buildScenario = new BuildScenario(scenario, version);
        allBuilds.add(buildScenario);
        return buildScenario.results;
    }

    public void writeTo(File csv) throws IOException {
        int maxRows = allBuilds.stream().mapToInt(v -> v.results.size()).max().getAsInt();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csv))) {
            writer.write("build");
            for (BuildScenario result : allBuilds) {
                String name = result.scenario.getName() + " " + result.gradleVersion.getVersion();
                writer.write(",");
                writer.write(name);
            }
            writer.newLine();
            writer.write("tasks");
            for (BuildScenario result : allBuilds) {
                writer.write(",");
                writer.write(result.scenario.getTasks().stream().collect(Collectors.joining(" ")));
            }
            writer.newLine();
            for (int row = 0; row < maxRows; row++) {
                for (BuildScenario result : allBuilds) {
                    List<BuildInvocationResult> results = result.results;
                    if (row >= results.size()) {
                        continue;
                    }
                    BuildInvocationResult buildResult = results.get(row);
                    writer.write(buildResult.getDisplayName());
                    break;
                }
                for (BuildScenario result : allBuilds) {
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

            List<DescriptiveStatistics> statistics = allBuilds.stream().map(BuildScenario::getStatistics).collect(Collectors.toList());
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

    private static class BuildScenario {
        private final ScenarioDefinition scenario;
        private final GradleVersion gradleVersion;
        private final List<BuildInvocationResult> results = new ArrayList<>();

        BuildScenario(ScenarioDefinition scenario, GradleVersion gradleVersion) {
            this.scenario = scenario;
            this.gradleVersion = gradleVersion;
        }

        public DescriptiveStatistics getStatistics() {
            DescriptiveStatistics statistics = new DescriptiveStatistics();
            if (results.size() > scenario.getWarmUpCount() + 1) {
                for (BuildInvocationResult result : results.subList(1 + scenario.getWarmUpCount(), results.size())) {
                    statistics.addValue(result.getExecutionTime().toMillis());
                }
            }
            return statistics;
        }

        @Override
        public boolean equals(Object obj) {
            throw new UnsupportedOperationException();
        }

    }
}
