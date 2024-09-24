package org.gradle.profiler.jfr;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.function.Consumer;

public class JfrProfiler extends InstrumentingProfiler {
    private final JFRArgs jfrArgs;

    JfrProfiler(JFRArgs jfrArgs) {
        this.jfrArgs = jfrArgs;
    }

    @Override
    public String toString() {
        return "JFR";
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".jfr")) {
            consumer.accept("JFR recording: " + resultFile.getAbsolutePath());
        } else if (resultFile.getName().endsWith(".jfr-flamegraphs")) {
            consumer.accept("JFR Flame Graphs: " + resultFile.getAbsolutePath());
        }
    }

    @Override
    public SnapshotCapturingProfilerController newSnapshottingController(ScenarioSettings settings) {
        return new JFRControl(jfrArgs, settings.computeJfrProfilerOutputLocation());
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        File jfrFile = settings.computeJfrProfilerOutputLocation();
        return new JFRJvmArgsCalculator(jfrArgs, startRecordingOnProcessStart, captureSnapshotOnProcessExit, jfrFile);
    }

    @Override
    public void validate(ScenarioSettings settings, Consumer<String> reporter) {
        validateMultipleIterationsWithCleanupAction(settings, reporter);
    }

    @Override
    protected boolean canRestartRecording(ScenarioSettings settings) {
        return !settings.getScenario().getInvoker().isReuseDaemon();
    }

    @Override
    public boolean isCreatesStacksFiles() {
        return true;
    }
}
