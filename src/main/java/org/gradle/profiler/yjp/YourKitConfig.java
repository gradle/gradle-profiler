package org.gradle.profiler.yjp;

public class YourKitConfig {
    private final boolean memorySnapshot;

    public YourKitConfig(boolean memorySnapshot) {
        this.memorySnapshot = memorySnapshot;
    }

    public boolean isMemorySnapshot() {
        return memorySnapshot;
    }
}
