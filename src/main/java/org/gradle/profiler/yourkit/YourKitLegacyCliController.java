package org.gradle.profiler.yourkit;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.InstrumentingProfiler;

import java.io.File;

/**
 * Controls the YourKit profiler agent via the legacy CLI approach (pre-2024.9).
 * <p>
 * Uses {@code java -jar yjp-controller-api-redist.jar} to send commands to the agent.
 * This JAR was an executable controller in versions before 2024.9, when it was replaced
 * by the HTTP API v2.
 *
 * @see <a href="https://www.yourkit.com/docs/java-profiler/latest/help/profiler-java-api.jsp">YourKit Profiler Java API</a>
 */
public class YourKitLegacyCliController implements InstrumentingProfiler.SnapshotCapturingProfilerController {

    private final YourKitConfig options;
    private final int port;

    public YourKitLegacyCliController(YourKitConfig options, int port) {
        this.options = options;
        this.port = port;
    }

    @Override
    public void startRecording(String pid) {
        if (options.memorySnapshot()) {
            runYourKitCommand("start-alloc-recording-adaptive");
        } else if (options.useSampling()) {
            runYourKitCommand("start-cpu-sampling");
        } else {
            runYourKitCommand("start-cpu-tracing");
        }
    }

    @Override
    public void stopRecording(String pid) {
        if (options.memorySnapshot()) {
            runYourKitCommand("stop-alloc-recording");
        } else {
            runYourKitCommand("stop-cpu-profiling");
        }
    }

    @Override
    public void captureSnapshot(String pid) {
        if (options.memorySnapshot()) {
            runYourKitCommand("capture-memory-snapshot");
        } else {
            runYourKitCommand("capture-performance-snapshot");
        }
    }

    private void runYourKitCommand(String command) {
        File controllerJar = findControllerJar();
        new CommandExec().run(System.getProperty("java.home") + "/bin/java",
            "-jar",
            controllerJar.getAbsolutePath(),
            "--host=localhost",
            "--port=" + port,
            command);
    }

    private File findControllerJar() {
        File controllerJar = YourKit.findControllerJar();
        if (controllerJar == null || !controllerJar.isFile()) {
            throw new IllegalArgumentException("Could not locate YourKit controller JAR in YourKit home directory " + YourKit.findYourKitHome());
        }
        return controllerJar;
    }
}
