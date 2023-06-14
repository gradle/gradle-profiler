package org.gradle.profiler.yourkit;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.function.Consumer;

public class YourKitProfiler extends InstrumentingProfiler {
    private final YourKitConfig yourKitConfig;

    YourKitProfiler(YourKitConfig yourKitConfig) {
        this.yourKitConfig = yourKitConfig;
    }

    @Override
    public String toString() {
        return "YourKit";
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".snapshot")) {
            consumer.accept(resultFile.getAbsolutePath());
        }
    }

    @Override
    protected boolean canRestartRecording(ScenarioSettings settings) {
        return false;
    }

    @Override
    protected SnapshotCapturingProfilerController doNewController(ScenarioSettings settings) {
        return new YourKitProfilerController(yourKitConfig);
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        return new YourKitJvmArgsCalculator(settings, yourKitConfig, startRecordingOnProcessStart, captureSnapshotOnProcessExit);
    }
}
