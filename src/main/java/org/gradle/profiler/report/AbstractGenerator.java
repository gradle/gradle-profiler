package org.gradle.profiler.report;

import org.gradle.profiler.InvocationSettings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Consumer;

public abstract class AbstractGenerator {
    private final File outputFile;

    public AbstractGenerator(File outputFile) {
        this.outputFile = outputFile;
    }

    public void write(InvocationSettings settings, BenchmarkResult result) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            write(settings, result, writer);
        }
    }

    protected abstract void write(InvocationSettings settings, BenchmarkResult result, BufferedWriter writer) throws IOException;

    public void summarizeResults(Consumer<String> consumer) {
        consumer.accept(outputFile.getAbsolutePath());
    }
}
