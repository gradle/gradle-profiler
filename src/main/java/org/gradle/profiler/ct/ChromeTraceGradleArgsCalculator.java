package org.gradle.profiler.ct;

import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

import java.util.List;

public class ChromeTraceGradleArgsCalculator extends GradleArgsCalculator {
    private final ScenarioSettings settings;

    public ChromeTraceGradleArgsCalculator(ScenarioSettings settings) {
        this.settings = settings;
    }

    @Override
    public void calculateGradleArgs(List<String> gradleArgs) {
        try {
            gradleArgs.addAll(new ChromeTraceInitScript(settings.getScenario().getOutputDir()).getArgs());
        } catch (Exception e) {
            throw new RuntimeException("Could not generate init script.", e);
        }
    }
}
