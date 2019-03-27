package org.gradle.profiler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class MavenScenarioInvoker extends ScenarioInvoker<MavenScenarioDefinition> {
    @Override
    void doRun(MavenScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        String mavenHome = System.getenv("MAVEN_HOME");
        String mvn = mavenHome == null ? "mvn" : mavenHome + "/bin/mvn";

        List<String> commandLine = new ArrayList<>();
        commandLine.add(mvn);
        commandLine.addAll(scenario.getTargets());

        BuildMutator mutator = scenario.getBuildMutator().get();
        mutator.beforeScenario();
        try {
            for (int i = 0; i < scenario.getWarmUpCount(); i++) {
                String displayName = WARM_UP.displayBuildNumber(i + 1);
                runMeasured(displayName, mutator, measureCommandLineExecution(displayName, commandLine, settings.getProjectDir()), resultConsumer);
            }
            for (int i = 0; i < scenario.getBuildCount(); i++) {
                String displayName = MEASURE.displayBuildNumber(i + 1);
                runMeasured(displayName, mutator, measureCommandLineExecution(displayName, commandLine, settings.getProjectDir()), resultConsumer);
            }
        } finally {
            mutator.afterScenario();
        }
    }
}
