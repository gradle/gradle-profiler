package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
        doRun(scenario, settings, resultConsumer, commandLine, envVars, ImmutableList.of(), ImmutableMap.of());
    }

    protected void doRun(
        T scenario,
        InvocationSettings settings,
        Consumer<BuildInvocationResult> resultConsumer,
        List<String> commandLine,
        Map<String, String> envVars,
        List<String> profileCommandLine,
        Map<String, String> profileEnvVars
    ) {
        ScenarioContext scenarioContext = ScenarioContext.from(settings, scenario);

        BuildMutator mutator = CompositeBuildMutator.from(scenario.getBuildMutators());
        mutator.beforeScenario(scenarioContext);
        try {
            for (int iteration = 1; iteration <= scenario.getWarmUpCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(WARM_UP, iteration);
                BuildStepAction<R> action = measureCommandLineExecution(commandLine, envVars, settings.getProjectDir(), settings.getBuildLog());
                runMeasured(buildContext, mutator, action, resultConsumer);
            }
            for (int iteration = 1; iteration <= scenario.getBuildCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(MEASURE, iteration);
                List<String> commandLineCombined = ImmutableList.<String>builder()
                    .addAll(commandLine)
                    .addAll(profileCommandLine)
                    .build();
                Map<String, String> envVarsCombined = ImmutableMap.<String, String>builder()
                    .putAll(envVars)
                    .putAll(profileEnvVars)
                    .build();
                BuildStepAction<R> action = measureCommandLineExecution(commandLineCombined, envVarsCombined, settings.getProjectDir(), settings.getBuildLog());
                runMeasured(buildContext, mutator, action, resultConsumer);
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
                Logging.detailed().println("  Command: " + commandLine);
                Logging.detailed().println("  Environment: " + envVars);
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
