package org.gradle.profiler;

import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BazelScenarioInvoker extends BuildToolCommandLineInvoker<BazelScenarioDefinition, BuildInvocationResult> {
    @Override
    void doRun(BazelScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        List<String> targets = scenario.getTargets();

        List<String> commandLine = new ArrayList<>();
        commandLine.add(scenario.getExecutablePath());
        commandLine.addAll(targets);

        doRun(scenario, settings, resultConsumer, commandLine);
    }
}
