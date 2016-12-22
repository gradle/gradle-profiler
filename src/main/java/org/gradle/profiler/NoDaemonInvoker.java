package org.gradle.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NoDaemonInvoker extends BuildInvoker {
    private final GradleVersion gradleVersion;
    private final File javaHome;
    private final File projectDir;

    public NoDaemonInvoker(GradleVersion gradleVersion, File javaHome, File projectDir, List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        super(jvmArgs, gradleArgs, pidInstrumentation, resultsConsumer);
        this.gradleVersion = gradleVersion;
        this.javaHome = javaHome;
        this.projectDir = projectDir;
    }

    @Override
    protected void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        List<String> commandLine = new ArrayList<>();
        gradleVersion.addGradleCommand(commandLine);
        commandLine.addAll(gradleArgs);
        commandLine.add("--no-daemon");
        commandLine.addAll(tasks);
        String gradleOpts = jvmArgs.stream().collect(Collectors.joining(" "));
        Logging.detailed().println("Running command:");
        for (String arg : commandLine) {
            Logging.detailed().println("  " + arg);
        }
        Logging.detailed().println("JAVA_HOME: " + javaHome.getAbsolutePath());
        Logging.detailed().println("GRADLE_OPTS: " + gradleOpts);
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.directory(projectDir);
        builder.environment().put("GRADLE_OPTS", gradleOpts);
        builder.environment().put("JAVA_HOME", javaHome.getAbsolutePath());
        builder.redirectErrorStream(true);
        try {
            new CommandExec().run(builder);
        } catch (Exception e) {
            System.out.println();
            System.out.println("ERROR: failed to run build. See log file for details.");
            System.out.println();
            throw new RuntimeException("Build failed.", e);
        }
    }
}
