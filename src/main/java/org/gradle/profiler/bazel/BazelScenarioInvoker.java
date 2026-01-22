package org.gradle.profiler.bazel;

import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.BuildToolCommandLineInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BazelScenarioInvoker extends BuildToolCommandLineInvoker<BazelScenarioDefinition, BuildInvocationResult> {
    @Override
    public void run(BazelScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        if (settings.isProfile()) {
            throw new IllegalArgumentException("Profiling is not supported for Bazel builds");
        }

        List<String> targets = scenario.getTargets();

        List<String> commandLine = new ArrayList<>();
        commandLine.add(scenario.getExecutablePath(settings.getProjectDir()));
        commandLine.addAll(targets);

        doRun(scenario, settings, resultConsumer, commandLine, ImmutableMap.of());
    }
}
