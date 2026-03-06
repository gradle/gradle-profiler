package org.gradle.profiler.yourkit;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Logging;
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
    public SnapshotCapturingProfilerController newSnapshottingController(ScenarioSettings settings) {
        if (YourKit.isHttpApiSupported()) {
            Logging.detailed().println("Using YourKit HTTP API v2 controller");
            return new YourKitHttpApiController(yourKitConfig, YourKit.PORT);
        } else {
            Logging.detailed().println("Using YourKit legacy CLI controller");
            return new YourKitLegacyCliController(yourKitConfig, YourKit.PORT);
        }
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        return new YourKitJvmArgsCalculator(settings, yourKitConfig, startRecordingOnProcessStart, captureSnapshotOnProcessExit);
    }
}
