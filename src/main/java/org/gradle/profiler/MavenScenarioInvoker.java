package org.gradle.profiler;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.gradle.profiler.Logging.startOperation;
import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class MavenScenarioInvoker extends ScenarioInvoker<MavenScenarioDefinition> {
    @Override
    void run(MavenScenarioDefinition scenario, InvocationSettings settings, BenchmarkResultCollector benchmarkResults) throws IOException, InterruptedException {
        String mavenHome = System.getenv("MAVEN_HOME");
        String mvn = mavenHome == null ? "mvn" : mavenHome + "/bin/mvn";

        System.out.println();
        System.out.println("* Maven targets: " + scenario.getTargets());

        List<String> commandLine = new ArrayList<>();
        commandLine.add(mvn);
        commandLine.addAll(scenario.getTargets());

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
