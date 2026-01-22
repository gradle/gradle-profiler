package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.SampleProvider;

import java.io.IOException;
import java.util.function.Consumer;

public abstract class ScenarioInvoker<T extends ScenarioDefinition, R extends BuildInvocationResult> {

    /**
     * Runs a scenario and collects the results.
     */
    public abstract void run(T scenario, InvocationSettings settings, Consumer<R> resultConsumer) throws IOException, InterruptedException;

    /**
     * Which samples will this invoker generate for the given settings?
     */
    public SampleProvider<R> samplesFor(InvocationSettings settings, T scenario) {
        return results -> ImmutableList.of(BuildInvocationResult.EXECUTION_TIME);
    }

    /**
     * Runs a single measured build and collects the result.
     */
    protected R runMeasured(BuildContext buildContext, BuildMutator buildMutator, BuildStepAction<? extends R> action, Consumer<? super R> consumer) {
        R result = new RunBuildStepAction<R>(action, buildMutator).run(buildContext, BuildStep.BUILD);
        consumer.accept(result);
        return result;
    }
}
