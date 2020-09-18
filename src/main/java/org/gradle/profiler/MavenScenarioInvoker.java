package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MavenScenarioInvoker extends BuildToolCommandLineInvoker<MavenScenarioDefinition, BuildInvocationResult> {
    @Override
    void doRun(MavenScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(scenario.getExecutablePath());
        commandLine.addAll(scenario.getTargets());

        doRun(scenario, settings, resultConsumer, commandLine);
    }
}
