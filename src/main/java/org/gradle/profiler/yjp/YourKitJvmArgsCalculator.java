package org.gradle.profiler.yjp;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.List;

public class YourKitJvmArgsCalculator extends JvmArgsCalculator {
    private final ScenarioSettings settings;
    private final YourKitConfig yourKitConfig;
    private final boolean instrumentWholeProcess;

    public YourKitJvmArgsCalculator(ScenarioSettings settings, YourKitConfig yourKitConfig, boolean instrumentWholeProcess) {
        this.settings = settings;
        this.yourKitConfig = yourKitConfig;
        this.instrumentWholeProcess = instrumentWholeProcess;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        if (!OperatingSystem.isMacOS() && !OperatingSystem.isLinuxX86()) {
            throw new IllegalArgumentException("YourKit is currently supported on OS X and Linux x64 only.");
        }
        File yourKitHome = YourKit.findYourKitHome();
        if (yourKitHome == null) {
            throw new IllegalArgumentException("Could not locate YourKit installation.");
        }
        File jnilib = YourKit.findJniLib();
        if (!jnilib.isFile()) {
            throw new IllegalArgumentException("Could not locate YourKit library in YourKit home directory " + yourKitHome);
        }
        String agentOptions = "-agentpath:" + jnilib.getAbsolutePath() + "=dir=" + settings.getScenario().getOutputDir().getAbsolutePath() + ",sessionname=" + settings.getScenario().getProfileName();
        if (yourKitConfig.isMemorySnapshot() || yourKitConfig.isUseSampling()) {
            agentOptions += ",disabletracing";
        } else {
            agentOptions += ",disablealloc";
        }
        if (instrumentWholeProcess) {
            if (yourKitConfig.isMemorySnapshot()) {
                agentOptions += ",alloceach=10,onexit=memory";
            } else if (yourKitConfig.isUseSampling()) {
                agentOptions += ",sampling,onexit=snapshot";
            } else {
                agentOptions += ",tracing,onexit=snapshot";
            }
        }
        jvmArgs.add(agentOptions);
    }
}
