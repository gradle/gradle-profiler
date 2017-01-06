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
    public void start() throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            String command = "start-alloc-recording-adaptive";
            runYourKitCommand(command);
        } else {
            String command = "start-cpu-tracing";
            runYourKitCommand(command);
        }
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            runYourKitCommand("stop-alloc-recording");
            runYourKitCommand("capture-memory-snapshot");
            runYourKitCommand("clear-alloc-data");
        } else {
            runYourKitCommand("stop-cpu-profiling");
            runYourKitCommand("capture-performance-snapshot");
            runYourKitCommand("clear-cpu-data");
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
