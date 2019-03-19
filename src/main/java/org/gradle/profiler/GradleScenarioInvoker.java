package org.gradle.profiler;

import org.apache.commons.io.FileUtils;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.gradle.profiler.BuildStep.BUILD;
import static org.gradle.profiler.BuildStep.CLEANUP;
import static org.gradle.profiler.Phase.MEASURE;
import static org.gradle.profiler.Phase.WARM_UP;

public class GradleScenarioInvoker extends ScenarioInvoker<GradleScenarioDefinition> {
    private final DaemonControl daemonControl;
    private final PidInstrumentation pidInstrumentation;

    public GradleScenarioInvoker(DaemonControl daemonControl, PidInstrumentation pidInstrumentation) {
        this.daemonControl = daemonControl;
        this.pidInstrumentation = pidInstrumentation;
    }

    @Override
    public void run(GradleScenarioDefinition scenario, InvocationSettings settings, BenchmarkResultCollector benchmarkResults) throws IOException, InterruptedException {
        ScenarioSettings scenarioSettings = new ScenarioSettings(settings, scenario);
        FileUtils.forceMkdir(scenario.getOutputDir());
        JvmArgsCalculator allBuildsJvmArgsCalculator = settings.getProfiler().newJvmArgsCalculator(scenarioSettings);
        GradleArgsCalculator allBuildsGradleArgsCalculator = settings.getProfiler().newGradleArgsCalculator(scenarioSettings);

        BuildAction cleanupAction = scenario.getCleanupAction();
        GradleBuildConfiguration buildConfiguration = scenario.getBuildConfiguration();

        daemonControl.stop(buildConfiguration);

        BuildMutator mutator = scenario.getBuildMutator().get();
        GradleConnector connector = GradleConnector.newConnector()
            .useInstallation(buildConfiguration.getGradleHome())
            .useGradleUserHomeDir(settings.getGradleUserHome().getAbsoluteFile());
        ProjectConnection projectConnection = connector.forProjectDirectory(settings.getProjectDir()).connect();
        try {
            buildConfiguration.printVersionInfo();
            List<String> allBuildsJvmArgs = new ArrayList<>(buildConfiguration.getJvmArguments());
            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                allBuildsJvmArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
            allBuildsJvmArgs.add("-Dorg.gradle.profiler.scenario=" + scenario.getName());
            allBuildsJvmArgsCalculator.calculateJvmArgs(allBuildsJvmArgs);
            logJvmArgs(allBuildsJvmArgs);
            List<String> allBuildsGradleArgs = new ArrayList<>(pidInstrumentation.getArgs());
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

            Consumer<BuildInvocationResult> resultsCollector = benchmarkResults.version(scenario);
            GradleInvoker buildInvoker;
            switch (scenario.getInvoker()) {
                case CliNoDaemon:
                    buildInvoker = new CliInvoker(buildConfiguration, buildConfiguration.getJavaHome(), settings.getProjectDir(), false);
                    break;
                case ToolingApi:
                case ToolingApiColdDaemon:
                    buildInvoker = new ToolingApiInvoker(projectConnection);
                    break;
                case Cli:
                case CliColdDaemon:
                    buildInvoker = new CliInvoker(buildConfiguration, buildConfiguration.getJavaHome(), settings.getProjectDir(), true);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            BuildAction beforeBuildAction;
            if (scenario.getInvoker().isColdDaemon()) {
                beforeBuildAction = new CleanupThenStopDaemon(cleanupAction,daemonControl, buildConfiguration);
            } else {
                beforeBuildAction = cleanupAction;
            }

            BuildUnderTestInvoker invoker = new BuildUnderTestInvoker(allBuildsJvmArgs, allBuildsGradleArgs, buildInvoker, pidInstrumentation);

            mutator.beforeScenario();

            BuildInvocationResult results = null;
            String pid = null;

            for (int i = 1; i <= scenario.getWarmUpCount(); i++) {
                int counter = i;
                beforeBuild(WARM_UP, counter, invoker, beforeBuildAction, mutator);
                String displayName = WARM_UP.displayBuildNumber(counter);
                results = runMeasured(displayName, mutator, () -> invoker.runBuild(WARM_UP, counter, BUILD, scenario.getAction()), resultsCollector);
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

            BuildUnderTestInvoker instrumentedBuildInvoker = invoker.withJvmArgs(instrumentedBuildJvmArgs).withGradleArgs(instrumentedBuildGradleArgs);

            if (settings.isProfile()) {
                if (pid == null) {
                    throw new IllegalStateException("Using the --profile option requires at least one warm-up");
                }
                Logging.startOperation("Starting profiler for daemon with pid " + pid);
                control.startSession();
            }
            for (int i = 1; i <= scenario.getBuildCount(); i++) {
                final int counter = i;
                beforeBuild(MEASURE, counter, invoker, beforeBuildAction, mutator);
                String displayName = MEASURE.displayBuildNumber(counter);
                results = runMeasured(displayName, mutator, () -> {
                    if (settings.isProfile() && (counter == 1 || beforeBuildAction.isDoesSomething())) {
                        try {
                            control.startRecording();
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    BuildInvocationResult result = instrumentedBuildInvoker.runBuild(MEASURE, counter, BUILD, scenario.getAction());

                    if (settings.isProfile() && (counter == scenario.getBuildCount() || beforeBuildAction.isDoesSomething())) {
                        try {
                            control.stopRecording();
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    return result;
                }, resultsCollector);
            }

            if (settings.isProfile()) {
                Logging.startOperation("Stopping profiler for daemon with pid " + pid);
                control.stopSession();
            }
            if (settings.isBenchmark()) {
                benchmarkResults.write();
            }
            Objects.requireNonNull(results);
            checkPid(pid, results.getDaemonPid(), scenario.getInvoker());
        } finally {
            mutator.afterScenario();
            projectConnection.close();
            daemonControl.stop(buildConfiguration);
        }
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

    private void beforeBuild(Phase phase, int buildNumber, BuildUnderTestInvoker invoker, BuildAction cleanupAction, BuildMutator mutator) {
        if (cleanupAction.isDoesSomething()) {
            String displayName = phase.displayBuildNumber(buildNumber);
            runCleanup(displayName, mutator, () -> invoker.runBuild(phase, buildNumber, CLEANUP, cleanupAction));
        }
    }

    private static void checkPid(String expected, String actual, Invoker invoker) {
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
