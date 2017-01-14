package org.gradle.profiler.bs;

import org.gradle.profiler.GradleArgsCalculator;

import java.io.IOException;
import java.util.List;

public class BuildScanGradleArgsCalculator extends GradleArgsCalculator {
    private final String buildScanVersion;

    public BuildScanGradleArgsCalculator(String buildScanVersion) {
        this.buildScanVersion = buildScanVersion;
    }

    @Override
    public void calculateGradleArgs(List<String> gradleArgs) {
        try {
            gradleArgs.addAll(new BuildScanInitScript(buildScanVersion).getArgs());
        } catch (IOException e) {
            throw new RuntimeException("Could not generate init script.", e);
        }
    }
}
