package org.gradle.profiler;

import java.util.List;

public interface BenchmarkResult {
    List<? extends BuildScenarioResult> getScenarios();
}
