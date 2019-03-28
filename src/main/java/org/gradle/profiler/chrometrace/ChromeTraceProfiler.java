package org.gradle.profiler.chrometrace;

import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.function.Consumer;

public class ChromeTraceProfiler extends Profiler {
    @Override
    public String toString() {
        return "chrome-trace";
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".html")) {
            consumer.accept(resultFile.getAbsolutePath());
        }
    }

    @Override
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return new ChromeTraceInstrumentation(settings);
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return gradleArgs -> gradleArgs.add("-Dtrace");
    }
}
