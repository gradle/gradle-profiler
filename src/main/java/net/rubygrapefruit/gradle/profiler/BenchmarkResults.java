package net.rubygrapefruit.gradle.profiler;

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
        }
    }

    private static class BuildScenario {
        private final ScenarioDefinition scenario;
        private final GradleVersion gradleVersion;
        private final List<BuildInvocationResult> results = new ArrayList<>();

        public BuildScenario(ScenarioDefinition scenario, GradleVersion gradleVersion) {
            this.scenario = scenario;
            this.gradleVersion = gradleVersion;
        }

        @Override
        public boolean equals(Object obj) {
            throw new UnsupportedOperationException();
        }

    }
}
