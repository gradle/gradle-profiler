package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.function.Consumer;

public class AsyncProfiler extends InstrumentingProfiler {
    private final AsyncProfilerConfig config;

    AsyncProfiler(AsyncProfilerConfig config) {
        this.config = config;
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        if (!startRecordingOnProcessStart && !captureSnapshotOnProcessExit) {
            // Can attach later instead
            return JvmArgsCalculator.DEFAULT;
        }
        return new AsyncProfilerJvmArgsCalculator(config, settings, captureSnapshotOnProcessExit);
    }

    @Override
    protected SnapshotCapturingProfilerController doNewController(ScenarioSettings settings) {
        return new AsyncProfilerController(config, settings);
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".svg")) {
            consumer.accept(resultFile.getAbsolutePath());
        }
    }

    @Override
    public String toString() {
        return "async profiler";
    }

    static File stacksFileFor(GradleScenarioDefinition scenario) {
        return new File(scenario.getOutputDir(), scenario.getProfileName() + ".stacks.txt");
    }
}
