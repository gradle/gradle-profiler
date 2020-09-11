package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class MavenScenarioInvoker extends ScenarioInvoker<MavenScenarioDefinition, BuildInvocationResult> {
    @Override
    void doRun(MavenScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        String mavenHome = System.getenv("MAVEN_HOME");
        String mvn = mavenHome == null ? "mvn" : mavenHome + "/bin/mvn";

        List<String> commandLine = new ArrayList<>();
        commandLine.add(mvn);
        commandLine.addAll(scenario.getTargets());

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
