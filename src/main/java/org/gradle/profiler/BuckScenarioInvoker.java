package org.gradle.profiler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.profiler.Logging.startOperation;
import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class BuckScenarioInvoker extends ScenarioInvoker<BuckScenarioDefinition> {
    @Override
    void run(BuckScenarioDefinition scenario, InvocationSettings settings, BenchmarkResultCollector benchmarkResults) {
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

        System.out.println();
        System.out.println("* Buck targets: " + targets);

        List<String> commandLine = new ArrayList<>();
        commandLine.add(buckwExe);
        commandLine.add("build");
        commandLine.addAll(targets);

        BuildMutator mutator = scenario.getBuildMutator().get();
        mutator.beforeScenario();
        try {
            Consumer<BuildInvocationResult> resultConsumer = benchmarkResults.version(scenario);
            for (int i = 0; i < scenario.getWarmUpCount(); i++) {
                String displayName = WARM_UP.displayBuildNumber(i + 1);
                mutator.beforeBuild();
                tryRun(() -> {
                    startOperation("Running " + displayName);
                    Timer timer = new Timer();
                    new CommandExec().inDir(settings.getProjectDir()).run(commandLine);
                    Duration executionTime = timer.elapsed();
                    Main.printExecutionTime(executionTime);
                    resultConsumer.accept(new BuildInvocationResult(displayName, executionTime, null));
                }, mutator::afterBuild);
            }
            for (int i = 0; i < scenario.getBuildCount(); i++) {
                String displayName = MEASURE.displayBuildNumber(i + 1);
                mutator.beforeBuild();
                tryRun(() -> {
                    startOperation("Running " + displayName);
                    Timer timer = new Timer();
                    new CommandExec().inDir(settings.getProjectDir()).run(commandLine);
                    Duration executionTime = timer.elapsed();
                    Main.printExecutionTime(executionTime);
                    resultConsumer.accept(new BuildInvocationResult(displayName, executionTime, null));
                }, mutator::afterBuild);
            }
        } finally {
            mutator.afterScenario();
        }
    }
}
