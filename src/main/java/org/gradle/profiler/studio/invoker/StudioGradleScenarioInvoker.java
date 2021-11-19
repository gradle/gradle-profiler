package org.gradle.profiler.studio.invoker;

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.GradleBuildInvocationResult;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.GradleScenarioInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioInvoker;
import org.gradle.profiler.result.Sample;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static org.gradle.profiler.studio.invoker.StudioSample.GRADLE_EXECUTION_TIME;
import static org.gradle.profiler.studio.invoker.StudioSample.IDE_EXECUTION_TIME;

public class StudioGradleScenarioInvoker extends ScenarioInvoker<StudioGradleScenarioDefinition, GradleBuildInvocationResult> {

    private final GradleScenarioInvoker gradleScenarioInvoker;

    public StudioGradleScenarioInvoker(GradleScenarioInvoker gradleScenarioInvoker) {
        this.gradleScenarioInvoker = gradleScenarioInvoker;
    }

    @Override
    public List<Sample<? super GradleBuildInvocationResult>> samplesFor(InvocationSettings settings, StudioGradleScenarioDefinition scenario) {
        GradleScenarioDefinition gradleScenarioDefinition = scenario.getGradleScenarioDefinition();
        List<Sample<? super GradleBuildInvocationResult>> gradleScenarioInvokerSamples = gradleScenarioInvoker.samplesFor(settings, gradleScenarioDefinition);
        return ImmutableList.<Sample<? super GradleBuildInvocationResult>>builder()
            .addAll(gradleScenarioInvokerSamples)
            .add(GRADLE_EXECUTION_TIME)
            .add(IDE_EXECUTION_TIME)
            .build();
    }

    @Override
    public void run(StudioGradleScenarioDefinition scenario, InvocationSettings settings, Consumer<GradleBuildInvocationResult> resultConsumer) throws IOException, InterruptedException {
        gradleScenarioInvoker.run(scenario.getGradleScenarioDefinition(), settings, resultConsumer);
    }
}
