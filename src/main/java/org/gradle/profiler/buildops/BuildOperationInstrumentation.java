package org.gradle.profiler.buildops;

import org.gradle.profiler.GradleInstrumentation;

import java.io.*;

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

    public String getConfigTime() {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                return reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read configuration time from file.", e);
        }
    }
}
