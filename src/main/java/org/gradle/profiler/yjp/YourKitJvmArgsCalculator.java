package org.gradle.profiler.yjp;

import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.List;

public class YourKitJvmArgsCalculator extends JvmArgsCalculator {
    private final ScenarioSettings settings;

    public YourKitJvmArgsCalculator(ScenarioSettings settings) {
        this.settings = settings;
    }

    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        if (!OperatingSystem.isMacOS() || !OperatingSystem.isLinuxX86()) {
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
        String agentOptions = "-agentpath:" + jnilib.getAbsolutePath() + "=dir=" + settings.getScenario().getOutputDir().getAbsolutePath() + ",sessionname=" + settings.getScenario().getName();
        YourKitConfig yourKitConfig = (YourKitConfig) settings.getInvocationSettings().getProfilerOptions();
        if (yourKitConfig.isMemorySnapshot()) {
            agentOptions += ",disabletracing";
        } else {
            agentOptions += ",disablealloc";
        }
        jvmArgs.add(agentOptions);
    }
}
