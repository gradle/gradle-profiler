package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.buildops.BuildOperationInstrumentation;
import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.gradle.profiler.BuildStep.CLEANUP;
import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class GradleScenarioInvoker extends ScenarioInvoker<GradleScenarioDefinition, GradleBuildInvocationResult> {
    private final DaemonControl daemonControl;
    private final PidInstrumentation pidInstrumentation;

    public GradleScenarioInvoker(DaemonControl daemonControl, PidInstrumentation pidInstrumentation) {
        this.daemonControl = daemonControl;
        this.pidInstrumentation = pidInstrumentation;
    }

    @Override
    public List<Sample<? super GradleBuildInvocationResult>> samplesFor(InvocationSettings settings, GradleScenarioDefinition scenario) {
        ImmutableList.Builder<Sample<? super GradleBuildInvocationResult>> builder = ImmutableList.builder();
        builder.add(BuildInvocationResult.EXECUTION_TIME);
        if (settings.isMeasureConfigTime()) {
            builder.add(GradleBuildInvocationResult.TIME_TO_TASK_EXECUTION);
        }
        scenario.getMeasuredBuildOperations().stream()
            .map(GradleBuildInvocationResult::sampleBuildOperation)
            .forEach(builder::add);
        return builder.build();
    }

    @Override
    public void doRun(GradleScenarioDefinition scenario, InvocationSettings settings, Consumer<GradleBuildInvocationResult> resultConsumer) throws IOException, InterruptedException {
        if (settings.isProfile() && scenario.getWarmUpCount() == 0) {
            throw new IllegalStateException("Using the --profile option requires at least one warm-up");
        }

        ScenarioSettings scenarioSettings = new ScenarioSettings(settings, scenario);
        FileUtils.forceMkdir(scenario.getOutputDir());
        JvmArgsCalculator allBuildsJvmArgsCalculator = settings.getProfiler().newJvmArgsCalculator(scenarioSettings);
        GradleArgsCalculator allBuildsGradleArgsCalculator = pidInstrumentation;
        allBuildsGradleArgsCalculator = allBuildsGradleArgsCalculator.plus(settings.getProfiler().newGradleArgsCalculator(scenarioSettings));

        BuildOperationInstrumentation buildOperationInstrumentation = new BuildOperationInstrumentation(
            settings.isMeasureConfigTime(),
            scenario.getMeasuredBuildOperations()
        );
        if (buildOperationInstrumentation.requiresInitScript()) {
            allBuildsGradleArgsCalculator = allBuildsGradleArgsCalculator.plus(buildOperationInstrumentation);
        }

        GradleBuildConfiguration buildConfiguration = scenario.getBuildConfiguration();

        daemonControl.stop(buildConfiguration);

        BuildMutator mutator = scenario.getBuildMutator().get();
        ScenarioContext scenarioContext = ScenarioContext.from(settings, scenario);
        GradleClient gradleClient = scenario.getInvoker().getClient().create(buildConfiguration, settings);
        try {
            buildConfiguration.printVersionInfo();

            List<String> allBuildsJvmArgs = new ArrayList<>(buildConfiguration.getJvmArguments());
            allBuildsJvmArgs.addAll(scenario.getJvmArgs());

            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                allBuildsJvmArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
            allBuildsJvmArgs.add("-Dorg.gradle.profiler.scenario=" + scenario.getName());
            allBuildsJvmArgsCalculator.calculateJvmArgs(allBuildsJvmArgs);
            logJvmArgs(allBuildsJvmArgs);

            List<String> allBuildsGradleArgs = new ArrayList<>();
            allBuildsGradleArgs.add("--gradle-user-home");
            allBuildsGradleArgs.add(settings.getGradleUserHome().getAbsolutePath());
            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                allBuildsGradleArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
            allBuildsGradleArgs.addAll(scenario.getGradleArgs());
            if (settings.isDryRun()) {
                allBuildsGradleArgs.add("--dry-run");
            }
            allBuildsGradleArgsCalculator.calculateGradleArgs(allBuildsGradleArgs);
            logGradleArgs(allBuildsGradleArgs);

            BuildUnderTestInvoker uninstrumented = new BuildUnderTestInvoker(allBuildsJvmArgs, allBuildsGradleArgs, gradleClient, pidInstrumentation, buildOperationInstrumentation);

            BuildStepAction<?> cleanupStep = cleanupStep(uninstrumented, mutator, scenario, buildConfiguration);
            BuildStepAction<GradleBuildInvocationResult> warmupBuildStep = buildStep(uninstrumented, scenario);

            mutator.beforeScenario(scenarioContext);

            GradleBuildInvocationResult results = null;
            String pid = null;

            for (int iteration = 1; iteration <= scenario.getWarmUpCount(); iteration++) {
                BuildContext buildContext = scenarioContext.withBuild(WARM_UP, iteration);
                cleanupStep.run(buildContext, CLEANUP);
                results = runMeasured(buildContext, mutator, warmupBuildStep, resultConsumer);
                if (pid == null) {
                    pid = results.getDaemonPid();
                } else {
                    checkPid(pid, results.getDaemonPid(), scenario.getInvoker());
                }
            }

            ProfilerController control = settings.getProfiler().newController(pid, scenarioSettings);

            List<String> instrumentedBuildJvmArgs = new ArrayList<>(allBuildsJvmArgs);
            settings.getProfiler().newInstrumentedBuildsJvmArgsCalculator(scenarioSettings).calculateJvmArgs(instrumentedBuildJvmArgs);

            List<String> instrumentedBuildGradleArgs = new ArrayList<>(allBuildsGradleArgs);
            settings.getProfiler().newInstrumentedBuildsGradleArgsCalculator(scenarioSettings).calculateGradleArgs(instrumentedBuildGradleArgs);

            Logging.detailed().println();
            Logging.detailed().println("* Using args for instrumented builds:");
            if (!instrumentedBuildJvmArgs.equals(allBuildsJvmArgs)) {
                logJvmArgs(instrumentedBuildJvmArgs);
            }
            if (!instrumentedBuildGradleArgs.equals(allBuildsGradleArgs)) {
                logGradleArgs(instrumentedBuildGradleArgs);
            }

            BuildUnderTestInvoker instrumentedBuildInvoker = uninstrumented.withJvmArgs(instrumentedBuildJvmArgs).withGradleArgs(instrumentedBuildGradleArgs);
            BuildStepAction<GradleBuildInvocationResult> measuredBuildStep = buildStep(instrumentedBuildInvoker, scenario);
            RecordingBuildStepAction recordingBuildStep = new RecordingBuildStepAction(measuredBuildStep, cleanupStep, scenario, control);

            control.startSession();
            for (int i = 1; i <= scenario.getBuildCount(); i++) {
                BuildContext buildContext = scenarioContext.withBuild(MEASURE, i);
                cleanupStep.run(buildContext, CLEANUP);
                results = runMeasured(buildContext, mutator, recordingBuildStep, resultConsumer);
            }

            control.stopSession();
            Objects.requireNonNull(results);
            checkPid(pid, results.getDaemonPid(), scenario.getInvoker());
        } finally {
            mutator.afterScenario(scenarioContext);
            gradleClient.close();
            daemonControl.stop(buildConfiguration);
        }
    }

    private BuildStepAction<GradleBuildInvocationResult> buildStep(BuildUnderTestInvoker invoker, GradleScenarioDefinition scenario) {
        return invoker.create(scenario.getAction());
    }

    private BuildStepAction<?> cleanupStep(BuildUnderTestInvoker invoker, BuildMutator buildMutator, GradleScenarioDefinition scenario, GradleBuildConfiguration buildConfiguration) {
        BuildStepAction<GradleBuildInvocationResult> cleanupStep = invoker.create(scenario.getCleanupAction());
        if (scenario.getInvoker().isShouldCleanUpDaemon()) {
            cleanupStep = new StopDaemonAfterAction<>(cleanupStep, daemonControl, buildConfiguration);
        }
        return new RunCleanupStepAction<>(cleanupStep, buildMutator);
    }

    private void logGradleArgs(List<String> allBuildsGradleArgs) {
        Logging.detailed().println("Gradle args:");
        for (String arg : allBuildsGradleArgs) {
            Logging.detailed().println("  " + arg);
        }
    }

    private void logJvmArgs(List<String> allBuildsJvmArgs) {
        Logging.detailed().println("JVM args:");
        for (String jvmArg : allBuildsJvmArgs) {
            Logging.detailed().println("  " + jvmArg);
        }
    }

    private static void checkPid(String expected, String actual, GradleBuildInvoker invoker) {
        if (invoker.isReuseDaemon()) {
            if (!expected.equals(actual)) {
                throw new RuntimeException("Multiple Gradle daemons were used.");
            }
        } else {
            if (expected.equals(actual)) {
                throw new RuntimeException("Gradle daemon was reused but should not be reused.");
            }
        }
    }
}
