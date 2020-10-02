package org.gradle.profiler.buildops;

import org.gradle.profiler.instrument.GradleInstrumentation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuildOperationInstrumentation extends GradleInstrumentation {
    private final boolean measureGarbageCollection;
    private final File totalGarbageCollectionTimeDataFile;
    private final boolean measureConfigTime;
    private final File configurationTimeDataFile;
    private final Map<String, File> buildOperationDataFiles;

    public BuildOperationInstrumentation(boolean measureGarbageCollection, boolean measureConfigTime, List<String> measuredBuildOperations) throws IOException {
        this.measureGarbageCollection = measureGarbageCollection;
        this.totalGarbageCollectionTimeDataFile = File.createTempFile("gradle-profiler", "gc-time");
        this.measureConfigTime = measureConfigTime;
        this.configurationTimeDataFile = File.createTempFile("gradle-profiler", "build-ops-config-time");
        this.configurationTimeDataFile.deleteOnExit();
        this.buildOperationDataFiles = measuredBuildOperations.stream()
            .collect(Collectors.toMap(Function.identity(), BuildOperationInstrumentation::createBuildOperationTempFile));
    }

    public boolean requiresInitScript() {
        return measureGarbageCollection || measureConfigTime || !buildOperationDataFiles.isEmpty();
    }

    private static File createBuildOperationTempFile(String op) {
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
        if (measureGarbageCollection) {
            writer.print(".measureGarbageCollection(" + newFile(totalGarbageCollectionTimeDataFile) + ")");
        }
        if (measureConfigTime) {
            writer.print(".measureConfigurationTime(" + newFile(configurationTimeDataFile) + ")");
        }
        if (!buildOperationDataFiles.isEmpty()) {
            writer.print(".measureBuildOperations(");
            buildOperationDataFiles.forEach((opName, dataFile) ->
                writer.print(String.format("'%s': %s,", opName, newFile(dataFile)))
            );
            writer.print(")");
        }
    }

    private String newFile(File dataFile) {
        return "new File(new URI('" + dataFile.toURI() + "'))";
    }

    /**
     * This is the cumulative total GC time since the process started, not the GC time of the current invocation.
     */
    public Optional<Duration> getTotalGarbageCollectionTime() {
        if (totalGarbageCollectionTimeDataFile.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(readCumulativeTimeFromDataFile(totalGarbageCollectionTimeDataFile));
    }

    public Optional<Duration> getTimeToTaskExecution() {
        // Should have two implementations instead
        if (configurationTimeDataFile.length() == 0) {
            return Optional.empty();
        }
        try {
            try (Stream<String> lines = Files.lines(configurationTimeDataFile.toPath(), StandardCharsets.UTF_8)) {
                return lines
                    .reduce((first, second) -> second)
                    .map(line -> Duration.ofMillis(Long.parseLong(line)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read result from file.", e);
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
            try (Stream<String> lines = Files.lines(dataFile.toPath(), StandardCharsets.UTF_8)) {
                return Duration.ofMillis(lines.mapToLong(Long::parseLong).sum());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read result from file.", e);
        }
    }
}
