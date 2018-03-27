package org.gradle.profiler.report;

import org.gradle.profiler.BuildScenarioResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public abstract class AbstractGenerator {
    private final File outputFile;

    public AbstractGenerator(File outputFile) {
        this.outputFile = outputFile;
    }

    public void write(List<? extends BuildScenarioResult> allScenarios) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            write(allScenarios, writer);
        }
    }

    protected abstract void write(List<? extends BuildScenarioResult> allScenarios, BufferedWriter writer) throws IOException;
}
