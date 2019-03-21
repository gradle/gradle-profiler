package org.gradle.profiler.yourkit;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class YourKitProfiler extends InstrumentingProfiler {
    private final YourKitConfig yourKitConfig;
    private OptionSpecBuilder memory;
    private OptionSpecBuilder sampling;

    public YourKitProfiler() {
        this(null);
    }

    private YourKitProfiler(YourKitConfig yourKitConfig) {
        this.yourKitConfig = yourKitConfig;
    }

    @Override
    public String toString() {
        return "YourKit";
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".snapshot")) {
            return Collections.singletonList(resultFile.getAbsolutePath());
        }
        return null;
    }

    @Override
    protected boolean canRestartRecording() {
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

    @Override
    public void addOptions(OptionParser parser) {
        memory = parser.accepts("yourkit-memory", "Perform memory profiling instead of CPU profiling");
        sampling = parser.accepts("yourkit-sampling", "Use sampling instead of tracing for CPU profiling");
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new YourKitProfiler(newConfigObject(parsedOptions));
    }

    private YourKitConfig newConfigObject(OptionSet parsedOptions) {
        YourKitConfig yourKitConfig = new YourKitConfig(parsedOptions.has(memory), parsedOptions.has(sampling));
        if (yourKitConfig.isMemorySnapshot() && yourKitConfig.isUseSampling()) {
            throw new IllegalArgumentException("Cannot use memory profiling and CPU sampling at the same time.");
        }
        return yourKitConfig;
    }
}
