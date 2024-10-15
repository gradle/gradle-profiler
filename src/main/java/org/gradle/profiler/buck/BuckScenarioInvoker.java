package org.gradle.profiler.buck;

import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.BuildToolCommandLineInvoker;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.result.BuildInvocationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BuckScenarioInvoker extends BuildToolCommandLineInvoker<BuckScenarioDefinition, BuildInvocationResult> {
    @Override
    public void run(BuckScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) {
        if (settings.isProfile()) {
            throw new IllegalArgumentException("Profiling is not supported for Buck builds");
        }

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

        doRun(scenario, settings, resultConsumer, commandLine, ImmutableMap.of());
    }
}
