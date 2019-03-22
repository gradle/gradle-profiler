package org.gradle.profiler;

import java.util.List;

public interface GradleArgsCalculator {
    GradleArgsCalculator DEFAULT = (args) -> {};

    void calculateGradleArgs(List<String> gradleArgs);

    default GradleArgsCalculator plus(GradleArgsCalculator other) {
        return (args) -> {
            this.calculateGradleArgs(args);
            other.calculateGradleArgs(args);
        };
    }
}
