package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class BuckScenarioInvoker extends ScenarioInvoker<BuckScenarioDefinition, BuildInvocationResult> {
    @Override
    void doRun(BuckScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        String buckwExe = settings.getProjectDir() + "/buckw";
        List<String> targets = new ArrayList<>(scenario.getTargets());
        if (scenario.getType() != null) {
            Logging.startOperation("Query targets with type " + scenario.getType());
            List<String> commandLine = new ArrayList<>();
            commandLine.add(buckwExe);
            commandLine.add("targets");
            if (!scenario.getType().equals("all")) {
                commandLine.add("--type");
                commandLine.add(scenario.getType());
            }
            String output = new CommandExec().inDir(settings.getProjectDir()).runAndCollectOutput(commandLine);
            targets.addAll(Arrays.stream(output.split("\\n")).filter(s -> s.matches("//\\w+.*")).collect(Collectors.toList()));
        }

        Logging.detailed().println("* Buck targets: " + targets);

        List<String> commandLine = new ArrayList<>();
        commandLine.add(buckwExe);
        commandLine.add("build");
        commandLine.addAll(targets);

        BuildMutator mutator = scenario.getBuildMutator().get();
        ScenarioContext scenarioContext = ScenarioContext.from(settings, scenario);

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
