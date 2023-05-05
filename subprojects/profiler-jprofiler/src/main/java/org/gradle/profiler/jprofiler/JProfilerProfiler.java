package org.gradle.profiler.jprofiler;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.function.Consumer;

public class JProfilerProfiler extends InstrumentingProfiler {
    private final JProfilerConfig jProfilerConfig;

    JProfilerProfiler(JProfilerConfig jProfilerConfig) {
        this.jProfilerConfig = jProfilerConfig;
    }

    @Override
    public String toString() {
        return "JProfiler";
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".jps")) {
            consumer.accept(resultFile.getAbsolutePath());
        }
    }

    @Override
    protected boolean canRestartRecording(ScenarioSettings settings) {
        return true;
    }

    @Override
    protected SnapshotCapturingProfilerController doNewController(ScenarioSettings settings) {
        return new JProfilerController(settings, jProfilerConfig);
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        return new JProfilerJvmArgsCalculator(jProfilerConfig, settings, startRecordingOnProcessStart, captureSnapshotOnProcessExit);
    }
}
