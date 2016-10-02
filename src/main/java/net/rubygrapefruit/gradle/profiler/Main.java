package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        System.out.println("* Details");
        System.out.println("Project dir: " + settings.getProjectDir());
        System.out.println("Profile: " + settings.isProfile());
        System.out.println("Benchmark: " + settings.isBenchmark());
        System.out.println("Tasks: " + settings.getTasks());

        startOperation("Probing build environment");
        DaemonControl daemonControl = new DaemonControl();
        GradleVersionInspector gradleVersionInspector = new GradleVersionInspector(settings.getProjectDir());
        List<GradleVersion> versions = new ArrayList<>();
        for (String v : settings.getVersions()) {
            GradleVersion version = gradleVersionInspector.resolve(v);
            versions.add(version);
            daemonControl.stop(version);
        }
        if (versions.isEmpty()) {
            GradleVersion version = gradleVersionInspector.defaultVersion();
            versions.add(version);
            daemonControl.stop(version);
        }

        System.out.println("Gradle versions:");
        for (GradleVersion version : versions) {
            System.out.println("  " + version.getVersion() + " (" + version.getGradleHome() + ")");
        }

        BenchmarkResults benchmarkResults = new BenchmarkResults();
        PidInstrumentation pidInstrumentation = new PidInstrumentation();
        JFRControl jfrControl = new JFRControl();
        JvmArgsCalculator jvmArgsCalculator = settings.isProfile() ? new JFRJvmArgsCalculator() : new JvmArgsCalculator();
        List<String> tasks = settings.getTasks();

        for (GradleVersion version : versions) {
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

                BuildInvoker invoker = new BuildInvoker(projectConnection, jvmArgs, pidInstrumentation, benchmarkResults.version(version));

                if (settings.isBenchmark()) {
                    List<String> cleanTasks = new ArrayList<>();
                    cleanTasks.add("clean");
                    cleanTasks.addAll(tasks);
                    invoker.runBuild("clean build", cleanTasks);
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
                    results = invoker.runBuild("build " + (i+1), tasks);
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

        if (settings.isBenchmark()) {
            benchmarkResults.writeTo(new File("benchmark.csv"));
        }

        return true;
    }

    private static void checkPid(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new RuntimeException("Multiple Gradle daemons were used. Please make sure all Gradle daemons are stopped before running the profiler.");
        }
    }
}
