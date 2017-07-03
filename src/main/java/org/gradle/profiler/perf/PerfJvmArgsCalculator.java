package org.gradle.profiler.perf;

import org.gradle.profiler.JvmArgsCalculator;

import java.util.List;

public class PerfJvmArgsCalculator extends JvmArgsCalculator {
    @Override
    public void calculateJvmArgs(List<String> jvmArgs) {
        jvmArgs.add("-XX:+PreserveFramePointer");
    }
}
