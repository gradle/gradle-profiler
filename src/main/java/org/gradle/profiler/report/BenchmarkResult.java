package org.gradle.profiler.report;

import java.util.List;

public interface BenchmarkResult {
    List<? extends BuildScenarioResult<?>> getScenarios();
}
