package org.gradle.profiler.yourkit;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.List;

import static org.gradle.profiler.yourkit.YourKit.ENVIRONMENT_VARIABLE;

public class YourKitJvmArgsCalculator implements JvmArgsCalculator {

    private final ScenarioSettings settings;
    private final YourKitConfig yourKitConfig;
    private final boolean startRecordingOnStart;
    private final boolean captureSnapshotOnProcessExit;

    public YourKitJvmArgsCalculator(ScenarioSettings settings, YourKitConfig yourKitConfig, boolean startRecordingOnStart, boolean captureSnapshotOnProcessExit) {
        this.settings = settings;
        this.yourKitConfig = yourKitConfig;
        this.startRecordingOnStart = startRecordingOnStart;
        this.captureSnapshotOnProcessExit = captureSnapshotOnProcessExit;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        // Wait for the port to be free before starting a new daemon with the YourKit agent.
        // A previous daemon may still be shutting down and holding the port.
        YourKit.waitForPortAvailable(YourKit.PORT);

        File yourKitHome = YourKit.findYourKitHome();
        if (yourKitHome == null) {
            throw new IllegalArgumentException("Could not locate YourKit installation. Try setting the " + ENVIRONMENT_VARIABLE + " environment variable");
        }
        File jnilib = YourKit.findJniLib();
        if (!jnilib.isFile()) {
            throw new IllegalArgumentException("Could not locate YourKit library in YourKit home directory " + yourKitHome);
        }
        String agentOptions = "-agentpath:" + jnilib.getAbsolutePath() + "=dir=" + settings.getProfilerOutputBaseDir().getAbsolutePath()
            + ",sessionname=" + settings.getProfilerOutputBaseName()
            + ",port=" + YourKit.PORT;
        if (yourKitConfig.memorySnapshot() || yourKitConfig.useSampling()) {
            agentOptions += ",disabletracing,probe_disable=*";
        } else {
            agentOptions += ",disablealloc";
        }
        if (startRecordingOnStart) {
            if (yourKitConfig.memorySnapshot()) {
                agentOptions += ",alloceach=10";
                if (captureSnapshotOnProcessExit) {
                    agentOptions += ",onexit=memory";
                }
            } else if (yourKitConfig.useSampling()) {
                agentOptions += ",sampling";
                if (captureSnapshotOnProcessExit) {
                    agentOptions += ",onexit=snapshot";
                }
            } else {
                agentOptions += ",tracing";
                if (captureSnapshotOnProcessExit) {
                    agentOptions += ",onexit=snapshot";
                }
            }
        }
        jvmArgs.add(agentOptions);
    }
}
