package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public abstract class BuildToolCommandLineInvoker<T extends BuildToolCommandLineScenarioDefinition, R extends BuildInvocationResult> extends ScenarioInvoker<T, R> {
    void doRun(T scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer, List<String> commandLine) {
        ScenarioContext scenarioContext = ScenarioContext.from(settings, scenario);

        BuildMutator mutator = new CompositeBuildMutator(scenario.getBuildMutators());
        mutator.beforeScenario(scenarioContext);
        try {
            for (int iteration = 1; iteration <= scenario.getWarmUpCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(WARM_UP, iteration);
                runMeasured(buildContext, mutator, measureCommandLineExecution(commandLine, settings.getProjectDir(), settings.getBuildLog()), resultConsumer);
            }
            for (int iteration = 1; iteration <= scenario.getBuildCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(MEASURE, iteration);
                runMeasured(buildContext, mutator, measureCommandLineExecution(commandLine, settings.getProjectDir(), settings.getBuildLog()), resultConsumer);
            }
        } finally {
            mutator.afterScenario(scenarioContext);
        }
    }

    /**
     * Returns a {@link Supplier} that returns the result of the given command.
     */
    private BuildStepAction<R> measureCommandLineExecution(List<String> commandLine, File workingDir, File buildLog) {
        return new BuildStepAction<R>() {
            @Override
            public boolean isDoesSomething() {
                return true;
            }

            @Override
            public R run(BuildContext buildContext, BuildStep buildStep) {
                Timer timer = new Timer();
                if (buildLog == null) {
                    new CommandExec().inDir(workingDir).run(commandLine);
                } else {
                    new CommandExec().inDir(workingDir).runAndCollectOutput(buildLog, commandLine);
                }
                Duration executionTime = timer.elapsed();
                return (R) new BuildInvocationResult(buildContext, executionTime);
            }
        };
    }
}
