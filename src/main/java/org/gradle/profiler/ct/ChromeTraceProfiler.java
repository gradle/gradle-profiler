package org.gradle.profiler.ct;

import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class ChromeTraceProfiler extends Profiler {
    @Override
    public String toString() {
        return "chrome-trace";
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".html")) {
            return Collections.singletonList(resultFile.getAbsolutePath());
        }
        return null;
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return new ChromeTraceGradleArgsCalculator(settings);
    }
}
