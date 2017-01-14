package org.gradle.profiler.bs;

import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.io.IOException;
import java.util.List;

public class BuildScanGradleArgsCalculator extends GradleArgsCalculator {
    private final ScenarioSettings settings;

    public BuildScanGradleArgsCalculator(ScenarioSettings settings) {
        this.settings = settings;
    }

    @Override
    public void calculateGradleArgs(List<String> gradleArgs) {
        String version = (String) settings.getInvocationSettings().getProfilerOptions();
        try {
            gradleArgs.addAll(new BuildScanInitScript(version).getArgs());
        } catch (IOException e) {
            throw new RuntimeException("Could not generate init script.", e);
        }
    }
}
