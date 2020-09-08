package org.gradle.profiler;

import org.gradle.profiler.report.AbstractGenerator;
import org.gradle.profiler.report.BenchmarkResult;
import org.gradle.profiler.report.BuildScenarioResult;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class BenchmarkResultCollector {
    private final List<BuildScenarioResultImpl<?>> allBuilds = new ArrayList<>();
    private final List<AbstractGenerator> generators;

    public BenchmarkResultCollector(AbstractGenerator... generators) {
        this.generators = Arrays.asList(generators);
    }

    public <S extends ScenarioDefinition, T extends BuildInvocationResult> Consumer<T> scenario(S scenario, List<Sample<? super T>> samples) {
        BuildScenarioResultImpl<T> buildScenario = new BuildScenarioResultImpl<>(scenario, baseLineFor(scenario), samples);
        allBuilds.add(buildScenario);
        return buildScenario;
    }

    @SuppressWarnings("unchecked")
    private <S extends ScenarioDefinition, T extends BuildInvocationResult> BuildScenarioResultImpl<T> baseLineFor(S scenario) {
        if (allBuilds.isEmpty()) {
            return null;
        }
        for (BuildScenarioResultImpl<?> candidate : allBuilds) {
            if (candidate.getScenarioDefinition().getName().equals(scenario.getName())) {
                return (BuildScenarioResultImpl<T>) candidate;
            }
        }
        if (allBuilds.size() >= 2) {
            if (allBuilds.get(allBuilds.size() - 1).getScenarioDefinition().getName().equals(allBuilds.get(allBuilds.size() - 2)
                .getScenarioDefinition().getName())) {
                return null;
            }
        }
        return (BuildScenarioResultImpl<T>) allBuilds.get(0);
    }

    public void write() throws IOException {
        for (AbstractGenerator generator : generators) {
            generator.write(new BenchmarkResultImpl());
        }
    }

    /**
     * Summarize the results for the user.
     */
    public void summarizeResults(Consumer<String> consumer) {
        for (AbstractGenerator generator : generators) {
            generator.summarizeResults(consumer);
        }
    }

    private class BenchmarkResultImpl implements BenchmarkResult {
        @Override
        public List<? extends BuildScenarioResult<?>> getScenarios() {
            return allBuilds;
        }
    }
}
