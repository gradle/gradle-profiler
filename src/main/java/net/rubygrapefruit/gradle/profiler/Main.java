package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.rubygrapefruit.gradle.profiler.Logging.*;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean ok;
        try {
            new Main().run(args);
            ok = true;
        } catch (CommandLineParser.SettingsNotAvailableException e) {
            // Reported already
            ok = false;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            ok = false;
        }
        System.out.println();
        System.out.flush();
        System.exit(ok ? 0 : 1);
    }

    public void run(String[] args) throws Exception {
        InvocationSettings settings = new CommandLineParser().parseSettings(args);

        setupLogging();

        System.out.println();
        System.out.println("* Settings");
        System.out.println("Project dir: " + settings.getProjectDir());
        System.out.println("Profile: " + settings.isProfile());
        System.out.println("Benchmark: " + settings.isBenchmark());
        System.out.println("Versions: " + settings.getVersions());
        System.out.println("Tasks: " + settings.getTasks());

        DaemonControl daemonControl = new DaemonControl();
        GradleVersionInspector gradleVersionInspector = new GradleVersionInspector(settings.getProjectDir(), daemonControl);
        ScenarioLoader scenarioLoader = new ScenarioLoader(gradleVersionInspector);
        List<ScenarioDefinition> scenarios = scenarioLoader.loadScenarios(settings);

        startOperation("Scenarios");
        for (ScenarioDefinition scenario : scenarios) {
            System.out.println("Scenario: " + scenario.getName());
            System.out.println("  Gradle versions:");
            for (GradleVersion version : scenario.getVersions()) {
                System.out.println("    " + version.getVersion() + " (" + version.getGradleHome() + ")");
            }
            System.out.println("  Tasks: " + scenario.getTasks());
            System.out.println("  Run using: " + scenario.getInvoker());
        }

        BenchmarkResults benchmarkResults = new BenchmarkResults();
        PidInstrumentation pidInstrumentation = new PidInstrumentation();
        JFRControl jfrControl = new JFRControl();
        JvmArgsCalculator jvmArgsCalculator = settings.isProfile() ? new JFRJvmArgsCalculator() : new JvmArgsCalculator();

        for (ScenarioDefinition scenario : scenarios) {
            startOperation("Running scenario " + scenario.getName());

            List<String> tasks = scenario.getTasks();

            for (GradleVersion version : scenario.getVersions()) {
                startOperation("Running using Gradle version " + version.getVersion());

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
                    jvmArgsCalculator.calculateJvmArgs(jvmArgs);
                    detailed().println("JVM args:");
                    for (String jvmArg : jvmArgs) {
                        detailed().println("  " + jvmArg);
                    }
                    detailed().println("Gradle args:");
                    for (String arg : pidInstrumentation.getArgs()) {
                        detailed().println("  " + arg);
                    }

                    Consumer<BuildInvocationResult> resultsCollector = benchmarkResults.version(scenario, version);
                    BuildInvoker invoker = scenario.getInvoker() == Invoker.NoDaemon ? new NoDaemonInvoker(version, javaHome, settings.getProjectDir(), jvmArgs, pidInstrumentation, resultsCollector) : new ToolingApiInvoker(projectConnection, jvmArgs, pidInstrumentation, resultsCollector);

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
