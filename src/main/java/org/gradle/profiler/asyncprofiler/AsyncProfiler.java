package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.function.Consumer;

public class AsyncProfiler extends InstrumentingProfiler {
    private final AsyncProfilerConfig config;
    private final String profilerName;

    AsyncProfiler(AsyncProfilerConfig config, String profilerName) {
        this.config = config;
        this.profilerName = profilerName;
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        if (!startRecordingOnProcessStart && !captureSnapshotOnProcessExit) {
            // Can attach later instead
            return JvmArgsCalculator.DEFAULT;
        }
        return new AsyncProfilerJvmArgsCalculator(config, new AsyncProfilerWorkspace(settings.getScenario(), profilerName), captureSnapshotOnProcessExit);
    }

    @Override
    protected SnapshotCapturingProfilerController doNewController(ScenarioSettings settings) {
        return new AsyncProfilerController(config, settings, new AsyncProfilerWorkspace(settings.getScenario(), profilerName));
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".svg") && resultFile.getParentFile().getName().equals(profilerName)) {
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
