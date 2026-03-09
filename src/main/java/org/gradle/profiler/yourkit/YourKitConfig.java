package org.gradle.profiler.yourkit;

public record YourKitConfig(
    boolean memorySnapshot,
    boolean useSampling
) {
}
