package org.gradle.profiler;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.profiler.Logging.*;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean ok;
        try {
            new Main().run(args);
            ok = true;
        } catch (Exception e) {
            // Reported already
            ok = false;
        }
        System.exit(ok ? 0 : 1);
    }

    public void run(String[] args) throws Exception {
        try {
            Instant started = Instant.now();
            InvocationSettings settings = new CommandLineParser().parseSettings(args);

            System.out.println();
            System.out.println("* Writing results to " + settings.getOutputDir().getAbsolutePath());

            Logging.setupLogging(settings.getOutputDir());

            Logging.detailed().println();
            Logging.detailed().println("* Started at " + started);

            Logging.startOperation("Settings");
            settings.printTo(System.out);

            DaemonControl daemonControl = new DaemonControl(settings.getGradleUserHome());
            GradleVersionInspector gradleVersionInspector = new GradleVersionInspector(settings.getProjectDir(), settings.getGradleUserHome(), daemonControl);
            ScenarioLoader scenarioLoader = new ScenarioLoader(gradleVersionInspector);
            List<ScenarioDefinition> scenarios = scenarioLoader.loadScenarios(settings);
            int totalScenarios = scenarios.size();

            logScenarios(scenarios);

            BenchmarkResults benchmarkResults = new BenchmarkResults();
            PidInstrumentation pidInstrumentation = new PidInstrumentation();
            File resultsFile = new File(settings.getOutputDir(), "benchmark.csv");

            List<Throwable> failures = new ArrayList<>();
            int scenarioCount = 0;

            for (ScenarioDefinition scenario : scenarios) {
                scenarioCount++;
                Logging.startOperation("Running scenario " + scenario.getDisplayName() + " (scenario " + scenarioCount + "/" + totalScenarios + ")");

                try {
                    if (scenario instanceof BuckScenarioDefinition) {
                        runBuckScenario((BuckScenarioDefinition) scenario, settings, benchmarkResults);
                    } else {
                        runGradleScenario((GradleScenarioDefinition)scenario, settings, daemonControl, benchmarkResults, pidInstrumentation, resultsFile);
                    }

                } catch (Throwable t) {
                    t.printStackTrace();
                    failures.add(t);
                }
            }

            if (settings.isBenchmark()) {
                benchmarkResults.writeTo(resultsFile);
            }

            System.out.println();
            System.out.println("* Results written to " + settings.getOutputDir().getAbsolutePath());

            if (!failures.isEmpty()) {
                throw new ScenarioFailedException(failures.get(0));
            }
        } catch (CommandLineParser.SettingsNotAvailableException | ScenarioFailedException e) {
            // Reported already
            throw e;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            throw e;
        } finally {
            System.out.println();
            System.out.flush();
        }
    }

    private void runGradleScenario(GradleScenarioDefinition scenario,  InvocationSettings settings, DaemonControl daemonControl, BenchmarkResults benchmarkResults,
                                   PidInstrumentation pidInstrumentation, File resultsFile) throws IOException, InterruptedException {
        ScenarioSettings scenarioSettings = new ScenarioSettings(settings, scenario);
        scenario.getOutputDir().mkdirs();
        JvmArgsCalculator allBuildsJvmArgsCalculator = settings.getProfiler().newJvmArgsCalculator(scenarioSettings);
        GradleArgsCalculator allBuildsGradleArgsCalculator = settings.getProfiler().newGradleArgsCalculator(scenarioSettings);

        List<String> cleanupTasks = scenario.getCleanupTasks();
        List<String> tasks = scenario.getTasks();
        GradleVersion version = scenario.getVersion();

        daemonControl.stop(version);

        GradleConnector connector = GradleConnector.newConnector()
                .useInstallation(version.getGradleHome())
                .useGradleUserHomeDir(settings.getGradleUserHome().getAbsoluteFile());
        ProjectConnection projectConnection = connector.forProjectDirectory(settings.getProjectDir()).connect();
        BuildMutator mutator = scenario.getBuildMutator().get();
        try {
            BuildEnvironment buildEnvironment = projectConnection.getModel(BuildEnvironment.class);
            Logging.detailed().println();
            Logging.detailed().println("* Build details");
            Logging.detailed().println("Gradle version: " + buildEnvironment.getGradle().getGradleVersion());

            File javaHome = buildEnvironment.getJava().getJavaHome();
            Logging.detailed().println("Java home: " + javaHome);
            Logging.detailed().println("OS name: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));

            List<String> allBuildsJvmArgs = new ArrayList<>(buildEnvironment.getJava().getJvmArguments());
            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                allBuildsJvmArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
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
            BuildInvoker invoker;
            switch (scenario.getInvoker()) {
                case NoDaemon:
                    invoker = new NoDaemonInvoker(version, javaHome, settings.getProjectDir(), allBuildsJvmArgs, allBuildsGradleArgs, pidInstrumentation, resultsCollector);
                    break;
                case ToolingApi:
                    invoker = new ToolingApiInvoker(projectConnection, allBuildsJvmArgs, allBuildsGradleArgs, pidInstrumentation, resultsCollector);
                    break;
                default:
                    throw new IllegalArgumentException();
            }

            if (settings.isBenchmark()) {
                Set<String> cleanTasks = new LinkedHashSet<>();
                cleanTasks.add("clean");
                cleanTasks.addAll(tasks);
                invoker.runBuild("initial clean build", new ArrayList<>(cleanTasks));
                daemonControl.stop(version);
            }

            beforeBuild(invoker, cleanupTasks, mutator);
            BuildInvocationResult results = invoker.runBuild("warm-up build 1", tasks);
            String pid = results.getDaemonPid();

            for (int i = 1; i < scenario.getWarmUpCount(); i++) {
                beforeBuild(invoker, cleanupTasks, mutator);
                results = invoker.runBuild("warm-up build " + (i + 1), tasks);
                checkPid(pid, results.getDaemonPid(), scenario.getInvoker());
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

            BuildInvoker instrumentedBuildInvoker = invoker.withJvmArgs(instrumentedBuildJvmArgs).withGradleArgs(instrumentedBuildGradleArgs);

            for (int i = 0; i < scenario.getBuildCount(); i++) {
                beforeBuild(invoker, cleanupTasks, mutator);

                if (settings.isProfile()) {
                    Logging.startOperation("Starting recording for daemon with pid " + pid);
                    control.start();
                }

                results = instrumentedBuildInvoker.runBuild("build " + (i + 1), tasks);

                if (settings.isProfile()) {
                    Logging.startOperation("Stopping recording for daemon with pid " + pid);
                    control.stop();
                }

                checkPid(pid, results.getDaemonPid(), scenario.getInvoker());

                // Flush results to file, in case this process crashes or fails in some way before completing all scenarios
                if (settings.isBenchmark()) {
                    benchmarkResults.writeTo(resultsFile);
                }
            }
        } finally {
            mutator.cleanup();
            projectConnection.close();
            daemonControl.stop(version);
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

    private void runBuckScenario(BuckScenarioDefinition scenario, InvocationSettings settings, BenchmarkResults benchmarkResults) throws IOException {
        String buckwExe = settings.getProjectDir() + "/buckw";
        List<String> targets = new ArrayList<>();
        targets.addAll(scenario.getTargets());
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

        System.out.println();
        System.out.println("* Buck targets: " + targets);

        List<String> commandLine = new ArrayList<>();
        commandLine.add(buckwExe);
        commandLine.add("build");
        commandLine.addAll(targets);

        BuildMutator mutator = scenario.getBuildMutator().get();
        try {
            Consumer<BuildInvocationResult> resultConsumer = benchmarkResults.version(scenario);
            for (int i = 0; i < scenario.getWarmUpCount(); i++) {
                String displayName = "warm-up build " + (i + 1);
                mutator.beforeBuild();

                startOperation("Running " + displayName);
                Timer timer = new Timer();
                new CommandExec().run(commandLine);
                Duration executionTime = timer.elapsed();
                System.out.println("Execution time " + executionTime.toMillis() + "ms");
                resultConsumer.accept(new BuildInvocationResult(displayName, executionTime, null));
            }
            for (int i = 0; i < scenario.getBuildCount(); i++) {
                String displayName = "build " + (i + 1);
                mutator.beforeBuild();

                startOperation("Running " + displayName);
                Timer timer = new Timer();
                new CommandExec().run(commandLine);
                Duration executionTime = timer.elapsed();
                System.out.println("Execution time " + executionTime.toMillis() + "ms");
                resultConsumer.accept(new BuildInvocationResult(displayName, executionTime, null));
            }
        } finally {
            mutator.cleanup();
        }
    }

    private void beforeBuild(BuildInvoker invoker, List<String> cleanupTasks, BuildMutator mutator) throws IOException {
        if (!cleanupTasks.isEmpty()) {
            invoker.notInstrumented().runBuild("cleanup", cleanupTasks);
        }
        mutator.beforeBuild();
    }

    private void logScenarios(List<ScenarioDefinition> scenarios) {
        Logging.startOperation("Scenarios");
        for (ScenarioDefinition scenario : scenarios) {
            scenario.printTo(System.out);
        }
    }

    private static void checkPid(String expected, String actual, Invoker invoker) {
        switch (invoker) {
            case ToolingApi:
                if (!expected.equals(actual)) {
                    throw new RuntimeException("Multiple Gradle daemons were used.");
                }
                break;
            case NoDaemon:
                if (expected.equals(actual)) {
                    throw new RuntimeException("Gradle daemon was used.");
                }
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    static class ScenarioFailedException extends RuntimeException {
        public ScenarioFailedException(Throwable cause) {
            super(cause);
        }
    }
}
