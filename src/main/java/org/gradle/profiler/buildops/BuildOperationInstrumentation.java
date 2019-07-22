package org.gradle.profiler.buildops;

import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.instrument.GradleInstrumentation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BuildOperationInstrumentation extends GradleInstrumentation {
    private final File configurationTimeDataFile;
    private final ImmutableMap<String, File> buildOperationDataFiles;
    private final boolean measureConfigTime;

    public BuildOperationInstrumentation(boolean measureConfigTime, List<String> measuredBuildOperations) throws IOException {
        this.measureConfigTime = measureConfigTime;
        this.configurationTimeDataFile = File.createTempFile("gradle-profiler", "build-ops-config-time");
        this.configurationTimeDataFile.deleteOnExit();
        this.buildOperationDataFiles = measuredBuildOperations.stream()
            .collect(ImmutableMap.toImmutableMap(Function.identity(), BuildOperationInstrumentation::createOpTmpFile));
    }

    public boolean requiresInitScript() {
        return measureConfigTime || !buildOperationDataFiles.isEmpty();
    }

    private static File createOpTmpFile(String op) {
        try {
            File tempFile = Files.createTempFile("gradle-profiler", "build-ops-" + op).toFile();
            tempFile.deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void generateInitScriptBody(PrintWriter writer) {
        writer.print("new org.gradle.trace.buildops.BuildOperationTrace(gradle)");
        if (measureConfigTime) {
            writer.print(".measureConfigurationTime(" + newFile(configurationTimeDataFile) + ")");
        }
        buildOperationDataFiles.forEach((opName, dataFile) ->
            writer.print(String.format(".measureBuildOperation('%s', %s)", opName, newFile(dataFile)))
        );
    }

    private String newFile(File dataFile) {
        return "new File(new URI('" + dataFile.toURI() + "'))";
    }

    public Optional<Duration> getTimeToTaskExecution() {
        // Should have two implementations instead
        if (configurationTimeDataFile.length() == 0) {
            return Optional.empty();
        }
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(configurationTimeDataFile))) {
                String last = null, line;
                while ((line = reader.readLine()) != null) {
                    last = line;
                }
                if (last == null) {
                    return Optional.empty();
                }
                return Optional.of(Duration.ofMillis(Long.parseLong(last)));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read result from file.", e);
        }
    }

    public Map<String, Duration> getCumulativeBuildOperationTimes() {
        return buildOperationDataFiles.entrySet().stream()
            .filter(entry -> entry.getValue().length() > 0)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                (entry -> readCumulativeTimeFromDataFile(entry.getValue())))
            );
    }

    private static Duration readCumulativeTimeFromDataFile(File dataFile) {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                int totalTime = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    totalTime += Long.parseLong(line);
                }
                return Duration.ofMillis(totalTime);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read result from file.", e);
        }
    }
}
