package org.gradle.profiler.report;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Phase;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CsvGenerator extends AbstractGenerator {
    private final Format format;
    private static final DecimalFormat DOUBLE_FORMAT = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.US));

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

        List<WideScenarioResults<?>> resultsByScenario = new ArrayList<>();
        for (BuildScenarioResult<?> scenario : allScenarios) {
            resultsByScenario.add(new WideScenarioResults<>(scenario));
        }
        int maxWarmUps = resultsByScenario.stream().mapToInt(WideScenarioResults::getWarmUpCount).max().orElse(0);
        int maxMeasured = resultsByScenario.stream().mapToInt(WideScenarioResults::getMeasureCount).max().orElse(0);
        writeWideRows(writer, resultsByScenario, Phase.WARM_UP, maxWarmUps);
        writeWideRows(writer, resultsByScenario, Phase.MEASURE, maxMeasured);
    }

    private void writeWideRows(BufferedWriter writer, List<WideScenarioResults<?>> resultsByScenario, Phase phase, int maxRows) throws IOException {
        for (int iteration = 1; iteration <= maxRows; iteration++) {
            writer.write(phase.displayBuildNumber(iteration));
            for (WideScenarioResults<?> scenarioResults : resultsByScenario) {
                writeWideRow(writer, phase, iteration, scenarioResults);
            }
            writer.newLine();
        }
    }

    private <T extends BuildInvocationResult> void writeWideRow(BufferedWriter writer, Phase phase, int iteration, WideScenarioResults<T> scenarioResults) throws IOException {
        T buildResult = scenarioResults.getResult(phase, iteration);
        for (Sample<? super T> sample : scenarioResults.getSamples()) {
            writer.write(",");
            if (buildResult != null) {
                writer.write(DOUBLE_FORMAT.format(sample.extractValue(buildResult)));
            }
        }
    }

    private void writeLong(BufferedWriter writer, List<? extends BuildScenarioResult<?>> allScenarios) throws IOException {
        writer.write("Scenario,Tool,Tasks,Phase,Iteration,Sample,Duration,Count");
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
                writer.write(DOUBLE_FORMAT.format(sample.extractValue(result)));
                writer.write(",");
                writer.write(String.valueOf(sample.extractTotalCountFrom(result)));
                writer.newLine();
            }
        }
    }

    private static class WideScenarioResults<T extends BuildInvocationResult> {
        private final List<Sample<? super T>> samples;
        private final Map<BuildKey, T> results = new HashMap<>();
        private int warmUpCount;
        private int measureCount;

        WideScenarioResults(BuildScenarioResult<T> scenario) {
            this.samples = scenario.getSamples();
            for (T result : scenario.getResults()) {
                Phase phase = result.getBuildContext().getPhase();
                int iteration = result.getBuildContext().getIteration();
                results.put(new BuildKey(phase, iteration), result);
                if (phase == Phase.WARM_UP) {
                    warmUpCount = Math.max(warmUpCount, iteration);
                } else if (phase == Phase.MEASURE) {
                    measureCount = Math.max(measureCount, iteration);
                }
            }
        }

        List<Sample<? super T>> getSamples() {
            return samples;
        }

        T getResult(Phase phase, int iteration) {
            return results.get(new BuildKey(phase, iteration));
        }

        int getWarmUpCount() {
            return warmUpCount;
        }

        int getMeasureCount() {
            return measureCount;
        }
    }

    private static class BuildKey {
        private final Phase phase;
        private final int iteration;

        BuildKey(Phase phase, int iteration) {
            this.phase = phase;
            this.iteration = iteration;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BuildKey)) {
                return false;
            }
            BuildKey other = (BuildKey) obj;
            return phase == other.phase && iteration == other.iteration;
        }

        @Override
        public int hashCode() {
            return 31 * phase.hashCode() + iteration;
        }
    }
}
