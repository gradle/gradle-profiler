package org.gradle.profiler.gradle;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.BuildStep;
import org.gradle.profiler.BuildStepAction;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Logging;
import org.gradle.profiler.Phase;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.RunBuildStepAction;
import org.gradle.profiler.RunCleanupStepAction;
import org.gradle.profiler.ScenarioContext;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.buildops.BuildOperationInstrumentation;
import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.result.Sample;
import org.gradle.profiler.result.SampleProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.gradle.profiler.BuildStep.CLEANUP;
import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

/**
 * Runs two {@link GradleScenarioDefinition}s in lock-step, alternating between them at every
 * iteration of warmup and measure. Both Gradle daemons stay alive for the duration of the run,
 * so build A and build B see the same time-correlated environmental noise, which improves the
 * statistical power of A/B comparisons (cross-version regression detection).
 *
 * Both scenarios must have the same warmup and measure counts.
 *
 * This invoker intentionally duplicates the lifecycle that {@link GradleScenarioInvoker} performs
 * for a single scenario, rather than refactoring it, to avoid risk to the existing single-scenario path.
 */
public class CrossVersionGradleScenarioInvoker {

    private final PidInstrumentation pidInstrumentation;

    public CrossVersionGradleScenarioInvoker(PidInstrumentation pidInstrumentation) {
        this.pidInstrumentation = pidInstrumentation;
    }

    /**
     * @deprecated The DaemonControl parameter is ignored. Each side constructs its own
     * DaemonControl from its own gradle-user-home; sharing one across sides causes
     * --stop to target the wrong user-home and leaves the other side's daemon alive,
     * which breaks any scenario that mutates state between iterations (e.g. clearing
     * the artifact transform cache).
     */
    @Deprecated
    public CrossVersionGradleScenarioInvoker(DaemonControl ignored, PidInstrumentation pidInstrumentation) {
        this(pidInstrumentation);
    }

    public SampleProvider<GradleBuildInvocationResult> samplesFor(InvocationSettings settings, GradleScenarioDefinition scenario) {
        return results -> {
            ImmutableList.Builder<Sample<? super GradleBuildInvocationResult>> builder = ImmutableList.builder();
            builder.add(org.gradle.profiler.result.BuildInvocationResult.EXECUTION_TIME);
            if (settings.isMeasureGarbageCollection()) {
                builder.add(GradleBuildInvocationResult.GARBAGE_COLLECTION_TIME);
            }
            if (settings.isMeasureLocalBuildCache()) {
                builder.add(GradleBuildInvocationResult.LOCAL_BUILD_CACHE_SIZE);
            }
            if (settings.isMeasureConfigTime()) {
                builder.add(GradleBuildInvocationResult.TIME_TO_TASK_EXECUTION);
            }
            scenario.getBuildOperationMeasurements().stream()
                .map(GradleBuildInvocationResult::sampleBuildOperation)
                .forEach(builder::add);
            return builder.build();
        };
    }

    public void run(
        GradleScenarioDefinition scenarioA, InvocationSettings settingsA, Consumer<GradleBuildInvocationResult> consumerA,
        GradleScenarioDefinition scenarioB, InvocationSettings settingsB, Consumer<GradleBuildInvocationResult> consumerB
    ) throws IOException, InterruptedException {
        if (scenarioA.getWarmUpCount() != scenarioB.getWarmUpCount()) {
            throw new IllegalArgumentException("Interleaved scenarios must have equal warmup counts (A=" + scenarioA.getWarmUpCount() + ", B=" + scenarioB.getWarmUpCount() + ")");
        }
        if (scenarioA.getBuildCount() != scenarioB.getBuildCount()) {
            throw new IllegalArgumentException("Interleaved scenarios must have equal build counts (A=" + scenarioA.getBuildCount() + ", B=" + scenarioB.getBuildCount() + ")");
        }

        Side sideA = new Side("A", scenarioA, settingsA, consumerA);
        Side sideB = new Side("B", scenarioB, settingsB, consumerB);

        try {
            sideA.setUp();
            sideB.setUp();

            int warmupCount = scenarioA.getWarmUpCount();
            int buildCount = scenarioA.getBuildCount();

            for (int iteration = 1; iteration <= warmupCount; iteration++) {
                sideA.runOneIteration(WARM_UP, iteration, false);
                sideB.runOneIteration(WARM_UP, iteration, false);
            }

            sideA.switchToInstrumented();
            sideB.switchToInstrumented();

            sideA.control.startSession();
            sideB.control.startSession();

            for (int iteration = 1; iteration <= buildCount; iteration++) {
                sideA.runOneIteration(MEASURE, iteration, true);
                sideB.runOneIteration(MEASURE, iteration, true);
            }

            sideA.control.stopSession();
            sideB.control.stopSession();
        } finally {
            sideA.tearDown();
            sideB.tearDown();
        }
    }

    /**
     * Holds the per-scenario state for one side of an interleaved cross-version run.
     */
    private class Side {
        final String label;
        final GradleScenarioDefinition scenario;
        final InvocationSettings settings;
        final Consumer<GradleBuildInvocationResult> consumer;
        final GradleBuildConfiguration buildConfiguration;
        final DaemonControl daemonControl;

        ScenarioSettings scenarioSettings;
        BuildOperationInstrumentation buildOperationInstrumentation;
        BuildMutator mutator;
        ScenarioContext scenarioContext;
        GradleClient gradleClient;
        List<String> allBuildsJvmArgs;
        List<String> allBuildsGradleArgs;
        BuildUnderTestInvoker uninstrumented;
        BuildStepAction<?> cleanupStep;
        BuildStepAction<GradleBuildInvocationResult> warmupBuildStep;
        BuildStepAction<GradleBuildInvocationResult> measuredBuildStep;
        RecordingBuildStepAction recordingBuildStep;
        ProfilerController control;
        String pid;

        Side(String label, GradleScenarioDefinition scenario, InvocationSettings settings, Consumer<GradleBuildInvocationResult> consumer) {
            this.label = label;
            this.scenario = scenario;
            this.settings = settings;
            this.consumer = consumer;
            this.buildConfiguration = scenario.getBuildConfiguration();
            // Each side must target its own gradle-user-home for daemon lifecycle commands;
            // sharing a DaemonControl across sides causes --stop to hit the wrong user-home.
            this.daemonControl = new DaemonControl(settings.getGradleUserHome());
        }

        void setUp() throws IOException {
            scenarioSettings = new ScenarioSettings(settings, scenario);
            FileUtils.forceMkdir(scenario.getOutputDir());

            JvmArgsCalculator jvmArgsCalculator = settings.getProfiler().newJvmArgsCalculator(scenarioSettings);
            GradleArgsCalculator gradleArgsCalculator = pidInstrumentation;
            gradleArgsCalculator = gradleArgsCalculator.plus(settings.getProfiler().newGradleArgsCalculator(scenarioSettings));

            buildOperationInstrumentation = new BuildOperationInstrumentation(
                settings.isMeasureGarbageCollection(),
                settings.isMeasureLocalBuildCache(),
                settings.isMeasureConfigTime(),
                scenario.getBuildOperationMeasurements()
            );
            if (buildOperationInstrumentation.requiresInitScript()) {
                gradleArgsCalculator = gradleArgsCalculator.plus(buildOperationInstrumentation);
            }

            daemonControl.stop(buildConfiguration);

            mutator = CompositeBuildMutator.from(scenario.getBuildMutators());
            scenarioContext = ScenarioContext.from(settings, scenario);
            gradleClient = scenario.getInvoker().getClient().create(buildConfiguration, settings);

            buildConfiguration.printVersionInfo();

            List<String> jvmArgs = new ArrayList<>(buildConfiguration.getJvmArguments());
            if (scenario.isBuildOperationsTrace()) {
                jvmArgs.add("-Dorg.gradle.internal.operations.trace.tree=false");
                jvmArgs.add("-Dorg.gradle.internal.operations.trace=" + scenario.getBuildOperationsTracePathPrefix());
            }
            jvmArgs.addAll(scenario.getJvmArgs());
            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                jvmArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
            jvmArgs.add("-Dorg.gradle.profiler.scenario=" + scenario.getName());
            jvmArgsCalculator.calculateJvmArgs(jvmArgs);
            this.allBuildsJvmArgs = jvmArgs;

            List<String> gradleArgs = new ArrayList<>();
            gradleArgs.add("--gradle-user-home");
            gradleArgs.add(settings.getGradleUserHome().getAbsolutePath());
            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                gradleArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
            gradleArgs.addAll(scenario.getGradleArgs());
            if (settings.isDryRun()) {
                gradleArgs.add("--dry-run");
            }
            gradleArgsCalculator.calculateGradleArgs(gradleArgs);
            this.allBuildsGradleArgs = gradleArgs;

            Logging.detailed().println("[side " + label + " / " + buildConfiguration.getGradleVersion() + "] uninstrumented args:");
            logArgs("JVM", allBuildsJvmArgs);
            logArgs("Gradle", allBuildsGradleArgs);

            uninstrumented = new BuildUnderTestInvoker(allBuildsJvmArgs, allBuildsGradleArgs, gradleClient, pidInstrumentation, buildOperationInstrumentation);
            cleanupStep = buildCleanupStep(uninstrumented);
            warmupBuildStep = uninstrumented.create(scenario.getAction());

            mutator.beforeScenario(scenarioContext);
        }

        void runOneIteration(Phase phase, int iteration, boolean instrumented) {
            BuildContext buildContext = scenarioContext.withBuild(phase, iteration);
            cleanupStep.run(buildContext, CLEANUP);
            BuildStepAction<GradleBuildInvocationResult> step = instrumented ? recordingBuildStep : warmupBuildStep;
            GradleBuildInvocationResult result = new RunBuildStepAction<>(step, mutator).run(buildContext, BuildStep.BUILD);
            consumer.accept(result);
            if (pid == null) {
                pid = result.getDaemonPid();
            } else {
                checkPid(pid, result.getDaemonPid(), scenario.getInvoker());
            }
        }

        void switchToInstrumented() {
            control = settings.getProfiler().newController(pid, scenarioSettings);

            List<String> instrumentedJvmArgs = new ArrayList<>(allBuildsJvmArgs);
            settings.getProfiler().newInstrumentedBuildsJvmArgsCalculator(scenarioSettings).calculateJvmArgs(instrumentedJvmArgs);

            List<String> instrumentedGradleArgs = new ArrayList<>(allBuildsGradleArgs);
            settings.getProfiler().newInstrumentedBuildsGradleArgsCalculator(scenarioSettings).calculateGradleArgs(instrumentedGradleArgs);

            if (!instrumentedJvmArgs.equals(allBuildsJvmArgs) || !instrumentedGradleArgs.equals(allBuildsGradleArgs)) {
                Logging.detailed().println("[side " + label + " / " + buildConfiguration.getGradleVersion() + "] instrumented args:");
                if (!instrumentedJvmArgs.equals(allBuildsJvmArgs)) {
                    logArgs("JVM", instrumentedJvmArgs);
                }
                if (!instrumentedGradleArgs.equals(allBuildsGradleArgs)) {
                    logArgs("Gradle", instrumentedGradleArgs);
                }
            }

            BuildUnderTestInvoker instrumented = uninstrumented.withJvmArgs(instrumentedJvmArgs).withGradleArgs(instrumentedGradleArgs);
            measuredBuildStep = instrumented.create(scenario.getAction());
            recordingBuildStep = new RecordingBuildStepAction(measuredBuildStep, cleanupStep, scenario, control);
        }

        void tearDown() {
            try {
                if (pid != null) {
                    // Already validated each iteration; nothing extra needed.
                    Objects.requireNonNull(pid);
                }
            } finally {
                try {
                    mutator.afterScenario(scenarioContext);
                } catch (Throwable ignored) {
                }
                try {
                    if (gradleClient != null) {
                        gradleClient.close();
                    }
                } catch (Throwable ignored) {
                }
                daemonControl.stop(buildConfiguration);
            }
        }

        private BuildStepAction<?> buildCleanupStep(BuildUnderTestInvoker invoker) {
            BuildStepAction<GradleBuildInvocationResult> cleanupAction = invoker.create(scenario.getCleanupAction());
            if (scenario.getInvoker().isShouldCleanUpDaemon()) {
                cleanupAction = new StopDaemonAfterAction<>(cleanupAction, daemonControl, buildConfiguration);
            }
            return new RunCleanupStepAction<>(cleanupAction, mutator);
        }
    }

    private static void logArgs(String kind, List<String> args) {
        Logging.detailed().println(kind + " args:");
        for (String arg : args) {
            Logging.detailed().println("  " + arg);
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
