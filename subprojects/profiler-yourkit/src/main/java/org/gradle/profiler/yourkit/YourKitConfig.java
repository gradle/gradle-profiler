package org.gradle.profiler.yourkit;

public class YourKitConfig {
    private final boolean memorySnapshot;
    private final boolean useSampling;

    public YourKitConfig(boolean memorySnapshot, boolean useSampling) {
        this.memorySnapshot = memorySnapshot;
        this.useSampling = useSampling;
    }

    public boolean isUseSampling() {
        return useSampling;
    }

    public boolean isMemorySnapshot() {
        return memorySnapshot;
    }
}
