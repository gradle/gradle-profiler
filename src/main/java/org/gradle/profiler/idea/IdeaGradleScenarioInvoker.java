package org.gradle.profiler.idea;

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioInvoker;
import org.gradle.profiler.gradle.GradleBuildInvocationResult;
import org.gradle.profiler.gradle.GradleScenarioInvoker;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SampleProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class IdeaGradleScenarioInvoker extends ScenarioInvoker<IdeaGradleScenarioDefinition, IdeaBuildInvocationResult> {

    private final GradleScenarioInvoker gradleScenarioInvoker;

    public IdeaGradleScenarioInvoker(GradleScenarioInvoker gradleScenarioInvoker) {
        this.gradleScenarioInvoker = gradleScenarioInvoker;
    }

    @Override
    public SampleProvider<IdeaBuildInvocationResult> samplesFor(InvocationSettings settings, IdeaGradleScenarioDefinition scenario) {
        return results -> {
            SampleProvider<GradleBuildInvocationResult> gradleSampleProvider = gradleScenarioInvoker.samplesFor(settings, scenario);
            List<Sample<? super GradleBuildInvocationResult>> gradleScenarioInvokerSamples = gradleSampleProvider.get(toGradleBuildInvocationResults(results));
            return ImmutableList.<Sample<? super IdeaBuildInvocationResult>>builder()
                .addAll(gradleScenarioInvokerSamples)
                .add(IdeaBuildInvocationResult.GRADLE_TOTAL_EXECUTION_TIME)
                .add(IdeaBuildInvocationResult.IDE_EXECUTION_TIME)
                .build();
        };
    }

    private List<GradleBuildInvocationResult> toGradleBuildInvocationResults(List<IdeaBuildInvocationResult> results) {
        // A cheap "cast" from List<IdeaBuildInvocationResult> to List<GradleBuildInvocationResult>
        return Collections.unmodifiableList(results);
    }

    @Override
    public void run(IdeaGradleScenarioDefinition scenario, InvocationSettings settings, Consumer<IdeaBuildInvocationResult> resultConsumer) throws IOException, InterruptedException {
        gradleScenarioInvoker.run(scenario, settings, (result) -> resultConsumer.accept(new IdeaBuildInvocationResult(result)));
    }
}

