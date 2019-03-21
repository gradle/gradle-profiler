package org.gradle.profiler.yourkit;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.InstrumentingProfiler;

import java.io.File;

public class YourKitProfilerController implements InstrumentingProfiler.SnapshotCapturingProfilerController {
    private final YourKitConfig options;

    public YourKitProfilerController(YourKitConfig options) {
        this.options = options;
    }

    @Override
    public void startRecording(String pid) {
        if (options.isMemorySnapshot()) {
            runYourKitCommand("start-alloc-recording-adaptive");
        } else if (options.isUseSampling()) {
            runYourKitCommand("start-cpu-sampling");
        } else {
            runYourKitCommand("start-cpu-tracing");
        }
    }

    @Override
    public void stopRecording(String pid) {
        if (options.isMemorySnapshot()) {
            runYourKitCommand("stop-alloc-recording");
        } else {
            runYourKitCommand("stop-cpu-profiling");
        }
    }

    @Override
    public void captureSnapshot(String pid) {
        if (options.isMemorySnapshot()) {
            runYourKitCommand("capture-memory-snapshot");
        } else {
            runYourKitCommand("capture-performance-snapshot");
        }
    }

    @Override
    public void stopSession() {
    }

    private void runYourKitCommand(String command) {
        File controllerJar = findControllerJar();
        new CommandExec().run(System.getProperty("java.home") + "/bin/java", "-jar", controllerJar.getAbsolutePath(), "localhost", String.valueOf(YourKitJvmArgsCalculator.PORT), command);
    }

    private File findControllerJar() {
        File yourKitHome = YourKit.findYourKitHome();
        File controllerJar = YourKit.findControllerJar();
        if (!controllerJar.isFile()) {
            throw new IllegalArgumentException("Could not locate YourKit library in YourKit home directory " + yourKitHome);
        }
        return controllerJar;
    }
}
