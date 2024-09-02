package org.gradle.profiler.maven;

import com.google.common.collect.ImmutableMap;
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
        scenario.getSystemProperties().forEach((key, value) ->
            commandLine.add(String.format("-D%s=%s", key, value)));

        doRun(scenario, settings, resultConsumer, commandLine, ImmutableMap.of());
    }
}
