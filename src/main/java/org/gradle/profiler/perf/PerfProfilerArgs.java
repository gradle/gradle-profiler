package org.gradle.profiler.perf;

public class PerfProfilerArgs {
    private final int frequency;
    private final int maxStack;

    public PerfProfilerArgs(int frequency, int maxStack) {
        this.frequency = frequency;
        this.maxStack = maxStack;
    }

    public int getFrequency() {
        return frequency;
    }

    public int getMaxStack() {
        return maxStack;
    }
}
