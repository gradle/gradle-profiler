package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.util.List;
import java.util.function.Consumer;

import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public abstract class BuildToolCommandLineInvoker<T extends BuildToolCommandLineScenarioDefinition, R extends BuildInvocationResult> extends ScenarioInvoker<T, R> {
    void doRun(T scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer, List<String> commandLine) {
        ScenarioContext scenarioContext = ScenarioContext.from(settings, scenario);

        BuildMutator mutator = scenario.getBuildMutator().get();
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
}
