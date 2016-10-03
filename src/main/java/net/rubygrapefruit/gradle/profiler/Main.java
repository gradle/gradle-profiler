package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            InvocationSettings settings = new CommandLineParser().parseSettings(args);

            setupLogging();

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

            for (ScenarioDefinition scenario : scenarios) {
                startOperation("Running scenario " + scenario.getName());

                List<String> tasks = scenario.getTasks();

                for (GradleVersion version : scenario.getVersions()) {
                    startOperation("Running scenario " + scenario.getName() + " using Gradle version " + version.getVersion());

                    startOperation("Stopping daemons");
                    daemonControl.stop(version);

                    GradleConnector connector = GradleConnector.newConnector().useInstallation(version.getGradleHome());
                    ProjectConnection projectConnection = connector.forProjectDirectory(settings.getProjectDir()).connect();
                    try {
                        BuildEnvironment buildEnvironment = projectConnection.getModel(BuildEnvironment.class);
                        detailed().println();
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
                            List<String> cleanTasks = new ArrayList<>();
                            cleanTasks.add("clean");
                            cleanTasks.addAll(tasks);
                            invoker.runBuild("initial clean build", cleanTasks);
                            startOperation("Stopping daemons");
                            daemonControl.stop(version);
                        }

                        BuildInvocationResult results = invoker.runBuild("warm-up build #1", tasks);
                        String pid = results.getDaemonPid();

                        results = invoker.runBuild("warm-up build #2", tasks);
                        checkPid(pid, results.getDaemonPid(), settings.getInvoker());

                        if (settings.isProfile()) {
                            startOperation("Starting recording for daemon with pid " + pid);
                            jfrControl.start(pid);
                        }

                        for (int i = 0; i < settings.getBuildCount(); i++) {
                            results = invoker.runBuild("build " + (i + 1), tasks);
                            checkPid(pid, results.getDaemonPid(), settings.getInvoker());
                        }

                        if (settings.isProfile()) {
                            startOperation("Stopping recording for daemon with pid " + pid);
                            jfrControl.stop(pid, new File("profile.jfr"));
                        }
                    } finally {
                        projectConnection.close();
                    }

                    startOperation("Stopping daemons");
                    daemonControl.stop(version);
                }
            }

            if (settings.isBenchmark()) {
                benchmarkResults.writeTo(new File("benchmark.csv"));
            }
        } catch (CommandLineParser.SettingsNotAvailableException e) {
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
}
