package org.gradle.profiler.buildops;

import org.gradle.profiler.instrument.GradleInstrumentation;

import javax.annotation.Nullable;
import java.io.*;
import java.time.Duration;

public class BuildOperationInstrumentation extends GradleInstrumentation {
    private final File dataFile;

    public BuildOperationInstrumentation() throws IOException {
        dataFile = File.createTempFile("gradle-profiler", "build-ops");
        dataFile.deleteOnExit();
    }

    @Override
    protected void generateInitScriptBody(PrintWriter writer) {
        writer.println("org.gradle.trace.buildops.BuildOperationTrace.start(gradle, new File(new URI('" + dataFile.toURI() + "')))");
    }

    @Nullable
    public Duration getTimeToTaskExecution() {
        // Should have two implementations instead
        if (dataFile.length() == 0) {
            return null;
        }
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                return Duration.ofMillis(Long.parseLong(reader.readLine()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read result from file.", e);
        }
    }
}
