package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class BazelScenarioInvoker extends ScenarioInvoker<BazelScenarioDefinition, BuildInvocationResult> {
    @Override
    void doRun(BazelScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        String bazelHome = System.getenv("BAZEL_HOME");
        String bazelExe = bazelHome == null ? "bazel" : bazelHome + "/bin/bazel";

        List<String> targets = scenario.getTargets();

        List<String> commandLine = new ArrayList<>();
        commandLine.add(bazelExe);
        commandLine.add("build");
        commandLine.addAll(targets);

        ScenarioContext scenarioContext = ScenarioContext.from(settings, scenario);

        BuildMutator mutator = scenario.getBuildMutator().get();
        mutator.beforeScenario(scenarioContext);
        try {
            for (int iteration = 1; iteration <= scenario.getWarmUpCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(WARM_UP, iteration);
                runMeasured(buildContext, mutator, measureCommandLineExecution(buildContext, commandLine, settings.getProjectDir()), resultConsumer);
            }
            for (int iteration = 1; iteration <= scenario.getBuildCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(MEASURE, iteration);
                runMeasured(buildContext, mutator, measureCommandLineExecution(buildContext, commandLine, settings.getProjectDir()), resultConsumer);
            }
        } finally {
            mutator.afterScenario(scenarioContext);
        }
    }
}
