package org.gradle.profiler.yjp;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.ProfilerController;

import java.io.File;
import java.io.IOException;

public class YourKitProfilerController implements ProfilerController {
    private final YourKitConfig options;

    public YourKitProfilerController(YourKitConfig options) {
        this.options = options;
    }

    @Override
    public void startSession() throws IOException, InterruptedException {

    }

    @Override
    public void startRecording() throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            runYourKitCommand("start-alloc-recording-adaptive");
        } else if (options.isUseSampling()) {
            runYourKitCommand("start-cpu-sampling");
        } else {
            runYourKitCommand("start-cpu-tracing");
        }
    }

    @Override
    public void stopRecording() throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            runYourKitCommand("stop-alloc-recording");
        } else {
            runYourKitCommand("stop-cpu-profiling");
        }
    }

    @Override
    public void stopSession() throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            runYourKitCommand("capture-memory-snapshot");
        } else {
            runYourKitCommand("capture-performance-snapshot");
        }
    }

    private void runYourKitCommand(String command) {
        File controllerJar = findControllerJar();
        new CommandExec().run(System.getProperty("java.home") + "/bin/java", "-jar", controllerJar.getAbsolutePath(), "localhost", "10001", command);
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
