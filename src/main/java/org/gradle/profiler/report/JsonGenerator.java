package org.gradle.profiler.report;

import org.gradle.profiler.InvocationSettings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.time.Instant;

public class JsonGenerator extends AbstractGenerator {
    public JsonGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(InvocationSettings settings, BenchmarkResult benchmarkResult, BufferedWriter writer) throws IOException {
        new JsonResultWriter(true).write(
            settings.getBenchmarkTitle(),
            Instant.now(),
            benchmarkResult.getScenarios(),
            writer
        );
    }
}
