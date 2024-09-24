package org.gradle.profiler.maven;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.profiler.BuildToolCommandLineInvoker;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.InstrumentingProfiler.SnapshotCapturingProfilerController;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.result.BuildInvocationResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MavenScenarioInvoker extends BuildToolCommandLineInvoker<MavenScenarioDefinition, BuildInvocationResult> {
    @Override
    public void run(MavenScenarioDefinition scenario, InvocationSettings settings, Consumer<BuildInvocationResult> resultConsumer) throws IOException, InterruptedException {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(scenario.getExecutablePath(settings.getProjectDir()));
        commandLine.addAll(scenario.getTargets());
        scenario.getSystemProperties().forEach((key, value) ->
            commandLine.add(String.format("-D%s=%s", key, value)));

        Map<String, String> profileEnvironment;
        SnapshotCapturingProfilerController controller;
        // TODO This only works with Async profiler, since the only thing we call from the controller is stopSession()
        //      Capture this in the type hierarchy somehow
        if (settings.getProfiler() instanceof InstrumentingProfiler) {
            InstrumentingProfiler profiler = (InstrumentingProfiler) settings.getProfiler();
            ScenarioSettings scenarioSettings = new ScenarioSettings(settings, scenario);
            List<String> mavenOpts = new ArrayList<>();
            profiler.newInstrumentedBuildsJvmArgsCalculator(scenarioSettings)
                .calculateJvmArgs(mavenOpts);
            profileEnvironment = ImmutableMap.of(
                "MAVEN_OPTS", String.join(" ", mavenOpts)
            );

            controller = profiler.newSnapshottingController(scenarioSettings);
        } else {
            profileEnvironment = ImmutableMap.of();
            controller = null;
        }

        doRun(scenario, settings, resultConsumer, commandLine, ImmutableMap.of(), ImmutableList.of(), profileEnvironment);

        if (controller != null) {
            controller.stopSession();
        }
    }
}
