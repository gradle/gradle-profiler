package org.gradle.profiler.yjp;

import org.gradle.profiler.ProfilerController;

import java.io.File;
import java.io.IOException;

public class YourKitProfilerController implements ProfilerController {
    @Override
    public void start() throws IOException, InterruptedException {
        String command = "start-cpu-tracing";
        runYourKitCommand(command);
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        runYourKitCommand("stop-cpu-tracing");
        runYourKitCommand("capture-performance-snapshot");
    }

    private void runYourKitCommand(String command) throws IOException, InterruptedException {
        File controllerJar = findControllerJar();
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-jar", controllerJar.getAbsolutePath(), "localhost", "10001",
                command).start();
        int result = process.waitFor();
        if (result != 0) {
            throw new RuntimeException("Command 'java' failed.");
        }
    }

    private File findControllerJar() {
        File yourKitHome = YourKit.findYourKitHome();
        File controllerJar = new File(yourKitHome, "Contents/Resources/lib/yjp-controller-api-redist.jar");
        if (!controllerJar.isFile()) {
            throw new IllegalArgumentException("Could not locate YourKit library in YourKit home directory " + yourKitHome);
        }
        return controllerJar;
    }
}
