package net.rubygrapefruit.gradle.profiler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.rubygrapefruit.gradle.profiler.Logging.*;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean ok;
        try {
            ok = run(args);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            ok = false;
        }
        System.out.println();
        System.out.flush();
        System.exit(ok ? 0 : 1);
    }

    private static boolean run(String[] args) throws Exception {
        InvocationSettings settings = new CommandLineParser().parseSettings(args);
        if (settings == null) {
            return false;
        }

        setupLogging();

        System.out.println();
        System.out.println("* Settings");
        System.out.println("Project dir: " + settings.getProjectDir());
        System.out.println("Profile: " + settings.isProfile());
        System.out.println("Benchmark: " + settings.isBenchmark());
        System.out.println("Versions: " + settings.getVersions());
        System.out.println("Tasks: " + settings.getTasks());

        startOperation("Probing build environment");

        DaemonControl daemonControl = new DaemonControl();
        GradleVersionInspector gradleVersionInspector = new GradleVersionInspector(settings.getProjectDir(), daemonControl);
        List<ScenarioDefinition> scenarios = new ArrayList<>();
        if (settings.getConfigFile() != null) {
            scenarios = loadConfig(settings.getConfigFile(), settings, gradleVersionInspector);
        }
        if (scenarios.isEmpty()) {
            List<GradleVersion> versions = new ArrayList<>();
            for (String v : settings.getVersions()) {
                versions.add(gradleVersionInspector.resolve(v));
            }
            if (versions.isEmpty()) {
                versions.add(gradleVersionInspector.defaultVersion());
            }
            scenarios.add(new ScenarioDefinition("default", versions, settings.getTasks()));
        }

        for (ScenarioDefinition scenario : scenarios) {
            System.out.println("Scenario: " + scenario.getName());
            System.out.println("  Gradle versions:");
            for (GradleVersion version : scenario.getVersions()) {
                System.out.println("    " + version.getVersion() + " (" + version.getGradleHome() + ")");
            }
            System.out.println("  Tasks: " + scenario.getTasks());
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

                daemonControl.stop(version);

                GradleConnector connector = GradleConnector.newConnector().useInstallation(version.getGradleHome());
                ProjectConnection projectConnection = connector.forProjectDirectory(settings.getProjectDir()).connect();
                try {
                    BuildEnvironment buildEnvironment = projectConnection.getModel(BuildEnvironment.class);
                    System.out.println("Gradle version: " + buildEnvironment.getGradle().getGradleVersion());
                    System.out.println("Java home: " + buildEnvironment.getJava().getJavaHome());
                    System.out.println("OS name: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
                    List<String> jvmArgs = new ArrayList<>(buildEnvironment.getJava().getJvmArguments());
                    jvmArgsCalculator.calculateJvmArgs(jvmArgs);
                    System.out.println("JVM args:");
                    for (String jvmArg : jvmArgs) {
                        System.out.println("  " + jvmArg);
                    }
                    System.out.println("Gradle args:");
                    for (String arg : pidInstrumentation.getArgs()) {
                        System.out.println("  " + arg);
                    }

                    BuildInvoker invoker = new BuildInvoker(projectConnection, jvmArgs, pidInstrumentation, benchmarkResults.version(scenario, version));

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
                    checkPid(pid, results.getDaemonPid());

                    if (settings.isProfile()) {
                        startOperation("Starting recording for daemon with pid " + pid);
                        jfrControl.start(pid);
                    }

                    for (int i = 0; i < settings.getBuildCount(); i++) {
                        results = invoker.runBuild("build " + (i + 1), tasks);
                        checkPid(pid, results.getDaemonPid());
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

        return true;
    }

    private static List<ScenarioDefinition> loadConfig(File configFile, InvocationSettings settings, GradleVersionInspector inspector) {
        List<ScenarioDefinition> definitions = new ArrayList<>();
        Config config = ConfigFactory.parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(false));
        for (String scenarioName : config.root().keySet()) {
            Config scenario = config.getConfig(scenarioName);
            List<GradleVersion> versions = strings(scenario, "versions", settings.getVersions()).stream().map(v -> inspector.resolve(v)).collect(
                    Collectors.toList());
            List<String> tasks = strings(scenario, "tasks", settings.getTasks());
            definitions.add(new ScenarioDefinition(scenarioName, versions, tasks));
        }
        return definitions;
    }

    private static List<String> strings(Config config, String key, List<String> defaults) {
        if (config.hasPath(key)) {
            Object value = config.getAnyRef(key);
            if (value instanceof List) {
                List<?> list = (List) value;
                return list.stream().map(v -> v.toString()).collect(Collectors.toList());
            } else {
                return Collections.singletonList(value.toString());
            }
        } else {
            return defaults;
        }
    }

    private static void checkPid(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new RuntimeException(
                    "Multiple Gradle daemons were used. Please make sure all Gradle daemons are stopped before running the profiler.");
        }
    }
}
