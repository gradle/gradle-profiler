package org.gradle.profiler.ct;

import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

public class ChromeTraceProfiler extends Profiler {
    @Override
    public String toString() {
        return "chrome-trace";
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return new ChromeTraceGradleArgsCalculator(settings);
    }
}
