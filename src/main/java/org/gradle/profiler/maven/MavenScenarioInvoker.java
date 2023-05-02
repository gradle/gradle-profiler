package org.gradle.profiler.maven;

import org.gradle.profiler.BuildToolCommandLineInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MavenScenarioInvoker extends BuildToolCommandLineInvoker<MavenScenarioDefinition, BuildInvocationResult> {
    @Override
    public void run(MavenScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(scenario.getExecutablePath());
        commandLine.addAll(scenario.getTargets());

        doRun(scenario, settings, resultConsumer, commandLine);
    }
}
