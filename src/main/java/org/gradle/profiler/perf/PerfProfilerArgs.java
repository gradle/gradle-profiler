package org.gradle.profiler.perf;

public class PerfProfilerArgs {
    private final int frequency;
    private final int maxStack;
    private final boolean unfold;

    public PerfProfilerArgs(int frequency, int maxStack, boolean unfold) {
        this.frequency = frequency;
        this.maxStack = maxStack;
        this.unfold = unfold;
    }

    public int getFrequency() {
        return frequency;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public boolean isUnfold() {
        return unfold;
    }
}
