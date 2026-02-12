package org.gradle.profiler.buildops.internal;

import org.gradle.profiler.buildops.BuildOperationMeasurement;
import org.gradle.profiler.buildops.BuildOperationMeasurementKind;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a request to measure a specific build operation during a Gradle build.
 */
public final class InternalBuildOpMeasurementRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient Path outputFile;
    private final String buildOperationType;
    private final BuildOperationMeasurementKind measurementKind;

    public InternalBuildOpMeasurementRequest(Path outputFile, String buildOperationType, BuildOperationMeasurementKind measurementKind) {
        this.outputFile = outputFile;
        this.buildOperationType = buildOperationType;
        this.measurementKind = measurementKind;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(outputFile.toString());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        String outputFilePath = in.readUTF();
        this.outputFile = Paths.get(outputFilePath);
    }

    /**
     * The file where the measurement results will be written.
     */
    public Path getOutputFile() {
        return outputFile;
    }

    /**
     * The type of build operation to be measured.
     */
    public String getBuildOperationType() {
        return buildOperationType;
    }

    /**
     * The kind of measurement to be performed on the build operation.
     */
    public BuildOperationMeasurementKind getMeasurementKind() {
        return measurementKind;
    }

    public BuildOperationMeasurement toPublicBuildOperationMeasurement() {
        return new BuildOperationMeasurement(buildOperationType, measurementKind);
    }
}
