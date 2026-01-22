package org.gradle.profiler;

import org.gradle.profiler.report.BuildScenarioResult;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SampleProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class BuildScenarioResultImpl<T extends BuildInvocationResult> implements BuildScenarioResult<T>, Consumer<T> {
    private final ScenarioDefinition scenario;
    private final SampleProvider<T> sampleProvider;
    private final List<T> results = new ArrayList<>();

    public BuildScenarioResultImpl(ScenarioDefinition scenario, SampleProvider<T> samplesProvider) {
        this.scenario = scenario;
        this.sampleProvider = samplesProvider;
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
        return sampleProvider.get(results);
    }

    @Override
    public List<T> getResults() {
        return Collections.unmodifiableList(results);
    }
}
