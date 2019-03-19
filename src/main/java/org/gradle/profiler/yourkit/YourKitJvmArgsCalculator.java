package org.gradle.profiler.yourkit;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.List;

import static org.gradle.profiler.yourkit.YourKit.ENIVONMENT_VARIABLE;

public class YourKitJvmArgsCalculator implements JvmArgsCalculator {
    public static final int PORT = 10021;
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
        if (!OperatingSystem.isMacOS() && !OperatingSystem.isLinuxX86()) {
            throw new IllegalArgumentException("YourKit is currently supported on OS X and Linux x64 only.");
        }
        File yourKitHome = YourKit.findYourKitHome();
        if (yourKitHome == null) {
            throw new IllegalArgumentException("Could not locate YourKit installation. Try setting the " + ENIVONMENT_VARIABLE + " environment variable");
        }
        File jnilib = YourKit.findJniLib();
        if (!jnilib.isFile()) {
            throw new IllegalArgumentException("Could not locate YourKit library in YourKit home directory " + yourKitHome);
        }
        String agentOptions = "-agentpath:" + jnilib.getAbsolutePath() + "=dir=" + settings.getScenario().getOutputDir().getAbsolutePath()
                + ",sessionname=" + settings.getScenario().getProfileName()
                + ",port=" + PORT;
        if (yourKitConfig.isMemorySnapshot() || yourKitConfig.isUseSampling()) {
            agentOptions += ",disabletracing,probe_disable=*";
        } else {
            agentOptions += ",disablealloc";
        }
        if (startRecordingOnStart) {
            if (yourKitConfig.isMemorySnapshot()) {
                agentOptions += ",alloceach=10";
                if (captureSnapshotOnProcessExit) {
                    agentOptions += ",onexit=memory";
                }
            } else if (yourKitConfig.isUseSampling()) {
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
