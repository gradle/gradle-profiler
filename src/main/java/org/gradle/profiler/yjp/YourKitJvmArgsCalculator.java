package org.gradle.profiler.yjp;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.JvmArgsCalculator;
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
        if (!System.getProperty("os.name").toLowerCase().contains("os x")) {
            throw new IllegalArgumentException("YourKit is currently supported on OS X only.");
        }
        File yourKitHome = YourKit.findYourKitHome();
        if (yourKitHome == null) {
            throw new IllegalArgumentException("Could not locate YourKit installation.");
        }
        File jnilib = new File(yourKitHome, "Contents/Resources/bin/mac/libyjpagent.jnilib");
        if (!jnilib.isFile()) {
            throw new IllegalArgumentException("Could not locate YourKit library in YourKit home directory " + yourKitHome);
        }
        String agentOptions = "-agentpath:" + jnilib.getAbsolutePath() + "=dir=" + settings.getScenarioOutputDir().getAbsolutePath() + ",sessionname=" + settings.getScenario().getName();
        YourKitConfig yourKitConfig = (YourKitConfig) settings.getInvocationSettings().getProfilerOptions();
        if (yourKitConfig.isMemorySnapshot()) {
            agentOptions += ",disabletracing";
        } else {
            agentOptions += ",disablealloc";
        }
        jvmArgs.add(agentOptions);
    }
}
