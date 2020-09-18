package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class ScenarioInvoker<T extends ScenarioDefinition, R extends BuildInvocationResult> {
    /**
     * Runs a scenario and collects the results.
     */
    public final void run(T scenario, InvocationSettings settings, BenchmarkResultCollector collector) throws IOException, InterruptedException {
        Consumer<R> resultConsumer = collector.scenario(scenario, samplesFor(settings, scenario));
        doRun(scenario, settings, resultConsumer);
    }

    /**
     * Runs a scenario and collects the results.
     */
    abstract void doRun(T scenario, InvocationSettings settings, Consumer<R> resultConsumer) throws IOException, InterruptedException;

    /**
     * Which samples will this invoker generate for the given settings?
     */
    public List<Sample<? super R>> samplesFor(InvocationSettings settings, T scenario) {
        return Collections.singletonList(BuildInvocationResult.EXECUTION_TIME);
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
