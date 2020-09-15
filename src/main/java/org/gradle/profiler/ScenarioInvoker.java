package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    /**
     * Returns a {@link Supplier} that returns the result of the given command.
     */
    BuildStepAction<BuildInvocationResult> measureCommandLineExecution(List<String> commandLine, File workingDir, File buildLog) {
        return new BuildStepAction<BuildInvocationResult>() {
            @Override
            public boolean isDoesSomething() {
                return true;
            }

            @Override
            public BuildInvocationResult run(BuildContext buildContext, BuildStep buildStep) {
                Timer timer = new Timer();
                if (buildLog == null) {
                    new CommandExec().inDir(workingDir).run(commandLine);
                } else {
                    new CommandExec().inDir(workingDir).runAndCollectOutput(buildLog, commandLine);
                }
                Duration executionTime = timer.elapsed();
                return new BuildInvocationResult(buildContext, executionTime);
            }
        };
    }
}
