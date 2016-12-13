package org.gradle.profiler.yjp;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.JvmArgsCalculator;

import java.io.File;
import java.util.List;

public class YourKitJvmArgsCalculator extends JvmArgsCalculator {
    private final InvocationSettings settings;

    public YourKitJvmArgsCalculator(InvocationSettings settings) {
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
        // Args for CPU tracing
        jvmArgs.add("-agentpath:" + jnilib.getAbsolutePath() + "=disablealloc,dir=" + settings.getOutputDir().getAbsolutePath());
    }
}
