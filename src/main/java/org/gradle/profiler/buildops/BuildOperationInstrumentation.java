package org.gradle.profiler.buildops;

import org.gradle.profiler.buildops.internal.InternalBuildOpMeasurementRequest;
import org.gradle.profiler.instrument.GradleInstrumentation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class BuildOperationInstrumentation extends GradleInstrumentation {
    private final boolean measureGarbageCollection;
    private final File totalGarbageCollectionTimeDataFile;

    private final boolean measureLocalBuildCache;
    private final File localBuildCacheDataFile;

    private final boolean measureConfigTime;
    private final File configurationTimeDataFile;

    private final List<InternalBuildOpMeasurementRequest> buildOpMeasurementRequests;

    public BuildOperationInstrumentation(
        boolean measureGarbageCollection,
        boolean measureLocalBuildCache,
        boolean measureConfigTime,
        List<BuildOperationMeasurement> buildOperationMeasurements
    ) throws IOException {
        this.measureGarbageCollection = measureGarbageCollection;
        this.totalGarbageCollectionTimeDataFile = File.createTempFile("gradle-profiler", "gc-time");
        this.measureLocalBuildCache = measureLocalBuildCache;
        this.localBuildCacheDataFile = File.createTempFile("gradle-profiler", "local-build-cache");
        this.measureConfigTime = measureConfigTime;
        this.configurationTimeDataFile = File.createTempFile("gradle-profiler", "build-ops-config-time");
        this.configurationTimeDataFile.deleteOnExit();
        this.buildOpMeasurementRequests = buildOperationMeasurements.stream()
            .map(e -> {
                Path outputFile = createBuildOperationTempFile(e);
                return new InternalBuildOpMeasurementRequest(outputFile, e.getBuildOperationType(), e.getMeasurementKind());
            })
            .toList();
    }

    public boolean requiresInitScript() {
        return measureGarbageCollection || measureLocalBuildCache || measureConfigTime || !buildOpMeasurementRequests.isEmpty();
    }

    private static Path createBuildOperationTempFile(BuildOperationMeasurement op) {
        try {
            Path tempFile = Files.createTempFile(
                "gradle-profiler",
                "build-ops-" + op.getBuildOperationType() + "_" + op.getMeasurementKind().name()
            );
            tempFile.toFile().deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void generateInitScriptBody(PrintWriter writer) {
        writer.print("new org.gradle.trace.buildops.BuildOperationTrace(gradle)");
        if (measureGarbageCollection) {
            writer.print(".measureGarbageCollection(" + fileExpression(totalGarbageCollectionTimeDataFile) + ")");
        }
        if (measureLocalBuildCache) {
            writer.print(".measureLocalBuildCache(" + fileExpression(localBuildCacheDataFile) + ")");
        }
        if (measureConfigTime) {
            writer.print(".measureConfigurationTime(" + fileExpression(configurationTimeDataFile) + ")");
        }
        if (!buildOpMeasurementRequests.isEmpty()) {
            writer.print(".measureBuildOperations([");
            buildOpMeasurementRequests.forEach(request -> {
                String buildOpTypeExpr = "'" + request.getBuildOperationType() + "'";
                String measurementKindExpr = BuildOperationMeasurementKind.class.getName() + "." +
                    request.getMeasurementKind().name();
                writer.print(
                    "new " + InternalBuildOpMeasurementRequest.class.getName() + "(\n" +
                        pathExpression(request.getOutputFile()) + ",\n" +
                        buildOpTypeExpr + ",\n" +
                        measurementKindExpr + ",\n" +
                        "),\n"
                );
            });
            writer.print("])");
        }
    }

    private static String fileExpression(File dataFile) {
        return pathExpression(dataFile.toPath()) + ".toFile()";
    }

    private static String pathExpression(Path file) {
        return Paths.class.getName() + ".get(new URI('" + file.toUri().toASCIIString() + "'))";
    }

    /**
     * This is the cumulative total GC time since the process started, not the GC time of the current invocation.
     */
    public Optional<Duration> getTotalGarbageCollectionTime() {
        if (totalGarbageCollectionTimeDataFile.length() == 0) {
            return Optional.empty();
        }

        return readExecutionDataFromFile(totalGarbageCollectionTimeDataFile.toPath())
            .map(BuildOperationExecutionData::getValue)
            .map(Duration::ofMillis);
    }

    public Optional<Long> getLocalBuildCacheSize() {
        if (localBuildCacheDataFile.length() == 0) {
            return Optional.empty();
        }

        return readExecutionDataFromFile(localBuildCacheDataFile.toPath())
            .map(BuildOperationExecutionData::getValue);
    }

    public Optional<Duration> getTimeToTaskExecution() {
        // Should have two implementations instead
        if (configurationTimeDataFile.length() == 0) {
            return Optional.empty();
        }

        return readExecutionDataFromFile(configurationTimeDataFile.toPath())
            .map(BuildOperationExecutionData::getValue)
            .map(Duration::ofMillis);
    }

    public Map<BuildOperationMeasurement, BuildOperationExecutionData> getTotalBuildOperationExecutionData() {
        return buildOpMeasurementRequests.stream()
            .filter(request -> {
                try {
                    return Files.size(request.getOutputFile()) > 0;
                } catch (IOException e) {
                    return false;
                }
            })
            .collect(Collectors.toMap(
                InternalBuildOpMeasurementRequest::toPublicBuildOperationMeasurement,
                request -> readExecutionDataFromFile(request.getOutputFile()).orElse(BuildOperationExecutionData.ZERO))
            );
    }

    private static Optional<BuildOperationExecutionData> readExecutionDataFromFile(Path dataFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read result from file.", e);
        }
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        // Expecting the file to contain at most one line
        // See `org.gradle.trace.buildops.BuildOperationTrace`
        String lastLine = lines.get(lines.size() - 1);
        return Optional.of(parseExecutionDataLine(lastLine));
    }

    private static BuildOperationExecutionData parseExecutionDataLine(String line) {
        int separatorIndex = line.indexOf(",");
        if (separatorIndex == -1) {
            throw new IllegalStateException("Unexpected line format: " + line);
        }

        long durationMillis = Long.parseLong(line.substring(0, separatorIndex));
        int count = Integer.parseInt(line.substring(separatorIndex + 1));
        return new BuildOperationExecutionData(durationMillis, count);
    }
}
