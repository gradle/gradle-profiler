package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

import static net.rubygrapefruit.gradle.profiler.Logging.*;

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

            setupLogging(settings.getOutputDir());

            Logging.detailed().println();
            Logging.detailed().println("* Started at " + started);

            logSettings(settings);

            DaemonControl daemonControl = new DaemonControl();
            GradleVersionInspector gradleVersionInspector = new GradleVersionInspector(settings.getProjectDir(), daemonControl);
            ScenarioLoader scenarioLoader = new ScenarioLoader(gradleVersionInspector);
            List<ScenarioDefinition> scenarios = scenarioLoader.loadScenarios(settings);

            logScenarios(scenarios);

            BenchmarkResults benchmarkResults = new BenchmarkResults();
            PidInstrumentation pidInstrumentation = new PidInstrumentation();
            JFRControl jfrControl = new JFRControl();
            JvmArgsCalculator jvmArgsCalculator = settings.isProfile() ? new JFRJvmArgsCalculator() : new JvmArgsCalculator();
            File resultsFile = new File(settings.getOutputDir(), "benchmark.csv");

            List<Throwable> failures = new ArrayList<>();

            for (ScenarioDefinition scenario : scenarios) {
                startOperation("Running scenario " + scenario.getName());

                List<String> tasks = scenario.getTasks();

                for (GradleVersion version : scenario.getVersions()) {
                    startOperation("Running scenario " + scenario.getName() + " using Gradle version " + version.getVersion());

                    try {
                        startOperation("Stopping daemons");
                        daemonControl.stop(version);

                        GradleConnector connector = GradleConnector.newConnector().useInstallation(version.getGradleHome());
                        ProjectConnection projectConnection = connector.forProjectDirectory(settings.getProjectDir()).connect();
                        try {
                            BuildEnvironment buildEnvironment = projectConnection.getModel(BuildEnvironment.class);
                            detailed().println();
                            detailed().println("* Build details");
                            detailed().println("Gradle version: " + buildEnvironment.getGradle().getGradleVersion());

                            File javaHome = buildEnvironment.getJava().getJavaHome();
                            detailed().println("Java home: " + javaHome);
                            detailed().println("OS name: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));

                            List<String> jvmArgs = new ArrayList<>(buildEnvironment.getJava().getJvmArguments());
                            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                                jvmArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
                            }
                            jvmArgsCalculator.calculateJvmArgs(jvmArgs);
                            detailed().println("JVM args:");
                            for (String jvmArg : jvmArgs) {
                                detailed().println("  " + jvmArg);
                            }
                            List<String> gradleArgs = new ArrayList<>(pidInstrumentation.getArgs());
                            for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                                gradleArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
                            }
                            gradleArgs.addAll(scenario.getGradleArgs());
                            if (settings.isDryRun()) {
                                gradleArgs.add("--dry-run");
                            }
                            detailed().println("Gradle args:");
                            for (String arg : gradleArgs) {
                                detailed().println("  " + arg);
                            }

                            Consumer<BuildInvocationResult> resultsCollector = benchmarkResults.version(scenario, version);
                            BuildInvoker invoker;
                            switch (scenario.getInvoker()) {
                                case NoDaemon:
                                    invoker = new NoDaemonInvoker(version, javaHome, settings.getProjectDir(), jvmArgs, gradleArgs, pidInstrumentation, resultsCollector);
                                    break;
                                case ToolingApi:
                                    invoker = new ToolingApiInvoker(projectConnection, jvmArgs, gradleArgs, pidInstrumentation, resultsCollector);
                                    break;
                                default:
                                    throw new IllegalArgumentException();
                            }

                            if (settings.isBenchmark()) {
                                Set<String> cleanTasks = new LinkedHashSet<>();
                                cleanTasks.add("clean");
                                cleanTasks.addAll(tasks);
                                invoker.runBuild("initial clean build", new ArrayList<>(cleanTasks));
                                startOperation("Stopping daemons");
                                daemonControl.stop(version);
                            }

                            BuildInvocationResult results = invoker.runBuild("warm-up build 1", tasks);
                            String pid = results.getDaemonPid();

                            for (int i = 1; i<settings.getWarmUpCount();i++) {
                                results = invoker.runBuild("warm-up build " + (i + 1), tasks);
                                checkPid(pid, results.getDaemonPid(), scenario.getInvoker());
                            }

                            if (settings.isProfile()) {
                                startOperation("Starting recording for daemon with pid " + pid);
                                jfrControl.start(pid);
                            }

                            for (int i = 0; i < settings.getBuildCount(); i++) {
                                results = invoker.runBuild("build " + (i + 1), tasks);
                                checkPid(pid, results.getDaemonPid(), scenario.getInvoker());

                                // Write results
                                if (settings.isBenchmark()) {
                                    benchmarkResults.writeTo(resultsFile);
                                }
                            }

                            if (settings.isProfile()) {
                                startOperation("Stopping recording for daemon with pid " + pid);
                                File profileSnapshotFile = new File(settings.getOutputDir(), "profile.jfr");
                                jfrControl.stop(pid, profileSnapshotFile);
                            }
                        } finally {
                            projectConnection.close();
                        }

                        startOperation("Stopping daemons");
                        daemonControl.stop(version);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        failures.add(t);
                    }
                }
            }

            if (settings.isBenchmark()) {
                benchmarkResults.writeTo(resultsFile);
            }

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

    private void logScenarios(List<ScenarioDefinition> scenarios) {
        startOperation("Scenarios");
        for (ScenarioDefinition scenario : scenarios) {
            System.out.println("Scenario: " + scenario.getName());
            System.out.println("  Gradle versions:");
            for (GradleVersion version : scenario.getVersions()) {
                System.out.println("    " + version.getVersion() + " (" + version.getGradleHome() + ")");
            }
            System.out.println("  Tasks: " + scenario.getTasks());
            System.out.println("  Run using: " + scenario.getInvoker());
            System.out.println("  Gradle args: " + scenario.getGradleArgs());
            if (!scenario.getSystemProperties().isEmpty()) {
                System.out.println("  System properties:");
                for (Map.Entry<String, String> entry : scenario.getSystemProperties().entrySet()) {
                    System.out.println("    " + entry.getKey() + "=" + entry.getValue());
                }
            }
        }
    }

    private void logSettings(InvocationSettings settings) {
        System.out.println();
        System.out.println("* Settings");
        System.out.println("Project dir: " + settings.getProjectDir());
        System.out.println("Output dir: " + settings.getOutputDir());
        System.out.println("Profile: " + settings.isProfile());
        System.out.println("Benchmark: " + settings.isBenchmark());
        System.out.println("Versions: " + settings.getVersions());
        System.out.println("Tasks: " + settings.getTasks());
        if (!settings.getSystemProperties().isEmpty()) {
            System.out.println("System properties:");
            for (Map.Entry<String, String> entry : settings.getSystemProperties().entrySet()) {
                System.out.println("  " + entry.getKey() + "=" + entry.getValue());
            }
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
