package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.report.BuildScenarioResult;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class BuildScenarioResultImpl<T extends BuildInvocationResult> implements BuildScenarioResult<T>, Consumer<T> {
    private final ScenarioDefinition scenario;
    private final List<Sample<? super T>> samples;
    private final List<T> results = new ArrayList<>();

    public BuildScenarioResultImpl(ScenarioDefinition scenario, List<Sample<? super T>> samples) {
        this.scenario = scenario;
        this.samples = ImmutableList.copyOf(samples);
    }

    @Override
    public void accept(T buildInvocationResult) {
        results.add(buildInvocationResult);
    }

    @Override
    public ScenarioDefinition getScenarioDefinition() {
        return scenario;
    }

    @Override
    public List<Sample<? super T>> getSamples() {
        return samples;
    }

    @Override
    public List<T> getResults() {
        return Collections.unmodifiableList(results);
    }
}
