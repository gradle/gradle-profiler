package org.gradle.profiler;

import org.gradle.profiler.result.BuildActionResult;
import org.gradle.profiler.result.BuildInvocationResult;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public abstract class BuildToolCommandLineInvoker<T extends BuildToolCommandLineScenarioDefinition, R extends BuildInvocationResult> extends ScenarioInvoker<T, R> {
    protected void doRun(T scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer, List<String> commandLine, Map<String, String> envVars) {
        ScenarioContext scenarioContext = ScenarioContext.from(settings, scenario);

        BuildMutator mutator = CompositeBuildMutator.from(scenario.getBuildMutators());
        mutator.beforeScenario(scenarioContext);
        try {
            for (int iteration = 1; iteration <= scenario.getWarmUpCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(WARM_UP, iteration);
                runMeasured(buildContext, mutator, measureCommandLineExecution(commandLine, envVars, settings.getProjectDir(), settings.getBuildLog()), resultConsumer);
            }
            for (int iteration = 1; iteration <= scenario.getBuildCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(MEASURE, iteration);
                runMeasured(buildContext, mutator, measureCommandLineExecution(commandLine, envVars, settings.getProjectDir(), settings.getBuildLog()), resultConsumer);
            }
        } finally {
            mutator.afterScenario(scenarioContext);
        }
    }

    /**
     * Returns a {@link Supplier} that returns the result of the given command.
     */
    private BuildStepAction<R> measureCommandLineExecution(List<String> commandLine, Map<String, String> envVars, File workingDir, File buildLog) {
        return new BuildStepAction<R>() {
            @Override
            public boolean isDoesSomething() {
                return true;
            }

            @Override
            public R run(BuildContext buildContext, BuildStep buildStep) {
                CommandExec commandExec = new CommandExec()
                    .inDir(workingDir)
                    .environmentVariables(envVars);
                Timer timer = new Timer();
                if (buildLog == null) {
                    commandExec.run(commandLine);
                } else {
                    commandExec.runAndCollectOutput(buildLog, commandLine);
                }
                Duration executionTime = timer.elapsed();
                return (R) new BuildInvocationResult(buildContext, new BuildActionResult(executionTime));
            }
        };
    }
}
