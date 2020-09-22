package org.gradle.profiler.report;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class CsvGenerator extends AbstractGenerator {
    private final Format format;

    public enum Format {
        LONG, WIDE;

        public static Format parse(String name) {
            for (Format format : values()) {
                if (format.name().toLowerCase().equals(name)) {
                    return format;
                }
            }
            throw new IllegalArgumentException("Unknown CSV format: " + name);
        }
    }

    public CsvGenerator(File outputFile, Format format) {
        super(outputFile);
        this.format = format;
    }

    @Override
    protected void write(InvocationSettings settings, BenchmarkResult benchmarkResult, BufferedWriter writer) throws IOException {
        List<? extends BuildScenarioResult<?>> allScenarios = benchmarkResult.getScenarios();
        switch (format) {
            case WIDE:
                writeWide(writer, allScenarios);
                break;
            case LONG:
                writeLong(writer, allScenarios);
                break;
            default:
                throw new AssertionError();
        }
    }

    private void writeWide(BufferedWriter writer, List<? extends BuildScenarioResult<?>> allScenarios) throws IOException {
        writer.write("scenario");
        for (BuildScenarioResult<?> scenario : allScenarios) {
            for (int i = 0; i < scenario.getSamples().size(); i++) {
                writer.write(",");
                writer.write(scenario.getScenarioDefinition().getTitle());
            }
        }
        writer.newLine();

        writer.write("version");
        for (BuildScenarioResult<?> scenario : allScenarios) {
            for (int i = 0; i < scenario.getSamples().size(); i++) {
                writer.write(",");
                writer.write(scenario.getScenarioDefinition().getBuildToolDisplayName());
            }
        }
        writer.newLine();

        writer.write("tasks");
        for (BuildScenarioResult<?> scenario : allScenarios) {
            for (int i = 0; i < scenario.getSamples().size(); i++) {
                writer.write(",");
                writer.write(scenario.getScenarioDefinition().getTasksDisplayName());
            }
        }
        writer.newLine();

        writer.write("value");
        for (BuildScenarioResult<?> scenario : allScenarios) {
            for (Sample<?> sample : scenario.getSamples()) {
                writer.write(",");
                writer.write(sample.getName());
            }
        }
        writer.newLine();

        int maxRows = allScenarios.stream().mapToInt(v -> v.getResults().size()).max().orElse(0);
        for (int row = 0; row < maxRows; row++) {
            for (BuildScenarioResult<?> scenario : allScenarios) {
                List<? extends BuildInvocationResult> results = scenario.getResults();
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                writer.write(buildResult.getBuildContext().getDisplayName());
                break;
            }
            for (BuildScenarioResult<?> scenario : allScenarios) {
                writeWideRow(writer, row, scenario);
            }
            writer.newLine();
        }
    }

    private <T extends BuildInvocationResult> void writeWideRow(BufferedWriter writer, int row, BuildScenarioResult<T> scenario) throws IOException {
        List<T> results = scenario.getResults();
        writer.write(",");
        if (row >= results.size()) {
            return;
        }
        T buildResult = results.get(row);
        writer.write(scenario.getSamples().stream()
            .map(sample -> sample.extractFrom(buildResult))
            .map(Duration::toMillis)
            .map(Object::toString)
            .collect(Collectors.joining(","))
        );
    }

    private void writeLong(BufferedWriter writer, List<? extends BuildScenarioResult<?>> allScenarios) throws IOException {
        writer.write("Scenario,Tool,Tasks,Phase,Iteration,Sample,Duration");
        writer.newLine();
        for (BuildScenarioResult<?> scenario : allScenarios) {
            writeLongRow(writer, scenario);
        }
    }

    private <T extends BuildInvocationResult> void writeLongRow(BufferedWriter writer, BuildScenarioResult<T> scenario) throws IOException {
        for (T result : scenario.getResults()) {
            for (Sample<? super T> sample : scenario.getSamples()) {
                writer.write(scenario.getScenarioDefinition().getTitle());
                writer.write(",");
                writer.write(scenario.getScenarioDefinition().getBuildToolDisplayName());
                writer.write(",");
                writer.write(scenario.getScenarioDefinition().getTasksDisplayName());
                writer.write(",");
                writer.write(result.getBuildContext().getPhase().name());
                writer.write(",");
                writer.write(String.valueOf(result.getBuildContext().getIteration()));
                writer.write(",");
                writer.write(sample.getName());
                writer.write(",");
                writer.write(String.valueOf(sample.extractFrom(result).toMillis()));
                writer.newLine();
            }
        }
    }
}
