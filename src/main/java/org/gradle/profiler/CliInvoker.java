package org.gradle.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CliInvoker extends BuildInvoker {
    private final GradleBuildConfiguration gradleBuildConfiguration;
    private final File javaHome;
    private final File projectDir;
    private final boolean daemon;


    public CliInvoker(GradleBuildConfiguration gradleBuildConfiguration, File javaHome, File projectDir, List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer, boolean daemon) {
        super(jvmArgs, gradleArgs, pidInstrumentation, resultsConsumer);
        this.gradleBuildConfiguration = gradleBuildConfiguration;
        this.javaHome = javaHome;
        this.projectDir = projectDir;
        this.daemon = daemon;
    }

    @Override
    protected BuildInvoker copy(List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        return new CliInvoker(gradleBuildConfiguration, javaHome, projectDir, jvmArgs, gradleArgs, pidInstrumentation, resultsConsumer, daemon);
    }

    @Override
    protected void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel) {
        if (toolingModel != null) {
            throw new UnsupportedOperationException("Cannot fetch a tooling model using the Gradle CLI.");
        }
        String gradleOpts = jvmArgs.stream().collect(Collectors.joining(" "));

        List<String> commandLine = new ArrayList<>();
        gradleBuildConfiguration.addGradleCommand(commandLine);
        commandLine.addAll(gradleArgs);
        commandLine.addAll(tasks);
        commandLine.add("-Dorg.gradle.daemon=" + daemon);
        if (daemon) {
            commandLine.add("-Dorg.gradle.jvmargs=" + gradleOpts);
        } else {
          commandLine.add("-Dorg.gradle.jvmargs");
        }

        Logging.detailed().println("Running command:");
        for (String arg : commandLine) {
            Logging.detailed().println("  " + arg);
        }
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.directory(projectDir);
        if (!daemon) {
            Logging.detailed().println("GRADLE_OPTS: " + gradleOpts);
            builder.environment().put("GRADLE_OPTS", gradleOpts);
        }
        Logging.detailed().println("JAVA_HOME: " + javaHome.getAbsolutePath());
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
