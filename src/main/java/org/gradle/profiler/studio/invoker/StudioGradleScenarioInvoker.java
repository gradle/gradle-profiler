package org.gradle.profiler.studio.invoker;

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.gradle.GradleBuildInvocationResult;
import org.gradle.profiler.gradle.GradleScenarioInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioInvoker;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SampleProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class StudioGradleScenarioInvoker extends ScenarioInvoker<StudioGradleScenarioDefinition, StudioBuildInvocationResult> {

    private final GradleScenarioInvoker gradleScenarioInvoker;

    public StudioGradleScenarioInvoker(GradleScenarioInvoker gradleScenarioInvoker) {
        this.gradleScenarioInvoker = gradleScenarioInvoker;
    }

    @Override
    public SampleProvider<StudioBuildInvocationResult> samplesFor(InvocationSettings settings, StudioGradleScenarioDefinition scenario) {
        return results -> {
            SampleProvider<GradleBuildInvocationResult> gradleSampleProvider = gradleScenarioInvoker.samplesFor(settings, scenario);
            List<Sample<? super GradleBuildInvocationResult>> gradleScenarioInvokerSamples = gradleSampleProvider.get(toGradleBuildInvocationResults(results));
            return ImmutableList.<Sample<? super StudioBuildInvocationResult>>builder()
                .addAll(gradleScenarioInvokerSamples)
                .addAll(getGradleExecutionTimeSamples(results))
                .add(StudioBuildInvocationResult.GRADLE_TOTAL_EXECUTION_TIME)
                .add(StudioBuildInvocationResult.IDE_EXECUTION_TIME)
                .build();
        };
    }

    private List<GradleBuildInvocationResult> toGradleBuildInvocationResults(List<StudioBuildInvocationResult> results) {
        // A cheap "cast" from List<StudioBuildInvocationResult> to List<GradleBuildInvocationResult>
        return Collections.unmodifiableList(results);
    }

    private List<Sample<StudioBuildInvocationResult>> getGradleExecutionTimeSamples(List<StudioBuildInvocationResult> results) {
        int maxGradleExecutions = results.stream()
            .mapToInt(result -> result.getActionResult().getGradleExecutionTimes().size())
            .max()
            .orElse(1);
        if (maxGradleExecutions <= 1) {
            // In case we have just one Gradle execution, we don't need to split it into multiple executions.
            // So we can return empty list here, and we will show only GRADLE_TOOLING_AGENT_TOTAL_EXECUTION_TIME.
            return Collections.emptyList();
        }
        return IntStream.range(0, maxGradleExecutions)
            .mapToObj(StudioBuildInvocationResult::getGradleToolingAgentExecutionTime)
            .collect(toList());
    }

    @Override
    public void run(StudioGradleScenarioDefinition scenario, InvocationSettings settings, Consumer<StudioBuildInvocationResult> resultConsumer) throws IOException, InterruptedException {
        gradleScenarioInvoker.run(scenario, settings, (result) -> resultConsumer.accept(new StudioBuildInvocationResult(result)));
    }
}
