package org.gradle.profiler.studio.invoker;

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.GradleBuildInvocationResult;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.GradleScenarioInvoker;
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
import static org.gradle.profiler.studio.invoker.StudioSample.GRADLE_EXECUTION_TIME;
import static org.gradle.profiler.studio.invoker.StudioSample.IDE_EXECUTION_TIME;

public class StudioGradleScenarioInvoker extends ScenarioInvoker<StudioGradleScenarioDefinition, GradleBuildInvocationResult> {

    private final GradleScenarioInvoker gradleScenarioInvoker;

    public StudioGradleScenarioInvoker(GradleScenarioInvoker gradleScenarioInvoker) {
        this.gradleScenarioInvoker = gradleScenarioInvoker;
    }

    @Override
    public SampleProvider<GradleBuildInvocationResult> samplesFor(InvocationSettings settings, StudioGradleScenarioDefinition scenario) {
        return results -> {
            GradleScenarioDefinition gradleScenarioDefinition = scenario.getGradleScenarioDefinition();

            List<Sample<? super GradleBuildInvocationResult>> gradleScenarioInvokerSamples = gradleScenarioInvoker.samplesFor(settings, gradleScenarioDefinition).get(results);
            return ImmutableList.<Sample<? super GradleBuildInvocationResult>>builder()
                .addAll(gradleScenarioInvokerSamples)
                .add(GRADLE_EXECUTION_TIME)
                .add(IDE_EXECUTION_TIME)
                .build();
        };
    }

    private List<Sample<GradleBuildInvocationResult>> getAllGradleToolingAgentExecutimeTimeSamples(List<GradleBuildInvocationResult> results) {
        int maxGradleExecutions = results.stream()
            .mapToInt(result -> ((StudioBuildActionResult) result.getActionResult()).getGradleExecutionTimes().size())
            .max()
            .orElse(1);
        if (maxGradleExecutions <= 1) {
            // In case we have just one Gradle execution, we don't need to split it into multiple executions.
            // So we can return empty list here, and we will show only GRADLE_TOOLING_AGENT_TOTAL_EXECUTION_TIME.
            return Collections.emptyList();
        }
        return IntStream.range(0, maxGradleExecutions)
            .mapToObj(GradleBuildInvocationResult::getGradleToolingAgentExecutionTime)
            .collect(toList());
    }

    @Override
    public void run(StudioGradleScenarioDefinition scenario, InvocationSettings settings, Consumer<GradleBuildInvocationResult> resultConsumer) throws IOException, InterruptedException {
        gradleScenarioInvoker.run(scenario.getGradleScenarioDefinition(), settings, resultConsumer);
    }
}
