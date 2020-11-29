package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.GradleScenarioDefinition;

import java.io.File;

public class AsyncProfilerWorkspace {
    private final File outputDir;

    public AsyncProfilerWorkspace(GradleScenarioDefinition scenario, String profilerName) {
        this.outputDir = new File(scenario.getOutputDir(), scenario.getProfileName() + "/" + profilerName);
        outputDir.mkdirs();
    }

    File getStacksFile() {
        return new File(outputDir, "stacks.txt");
    }

    File getSimplifiedStacksFile() {
        return new File(outputDir, "simplified-stacks.txt");
    }

    File getFlamesFile() {
        return new File(outputDir, "flames.svg");
    }

    File getIciclesFile() {
        return new File(outputDir, "icicles.svg");
    }
}
