package org.gradle.profiler;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CliGradleClient implements GradleInvoker, GradleClient {
    private final GradleBuildConfiguration gradleBuildConfiguration;
    private final File javaHome;
    private final File projectDir;
    private final boolean daemon;
    private final File buildLog;

    public CliGradleClient(GradleBuildConfiguration gradleBuildConfiguration,
                      File javaHome,
                      File projectDir,
                      boolean daemon,
                      File buildLog) {
        this.gradleBuildConfiguration = gradleBuildConfiguration;
        this.javaHome = javaHome;
        this.projectDir = projectDir;
        this.daemon = daemon;
        this.buildLog = buildLog;
    }

    @Override
    public void close() {
    }

    @Override
    public void loadToolingModel(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel) {
        throw new UnsupportedOperationException("Cannot fetch a tooling API model using the Gradle CLI.");
    }

    @Override
    public <T> T runToolingAction(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, BuildAction<T> action, Consumer<BuildActionExecuter<?>> configureAction) {
        throw new UnsupportedOperationException("Cannot run a tooling API action using the Gradle CLI.");
    }

    @Override
    public void runTasks(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        String gradleOpts = quoteJvmArguments(daemon, jvmArgs);

        List<String> commandLine = new ArrayList<>();
        gradleBuildConfiguration.addGradleCommand(commandLine);
        commandLine.addAll(gradleArgs);
        commandLine.addAll(tasks);
        commandLine.add("-Dorg.gradle.daemon=" + daemon);
        if (!daemon) {
            commandLine.add("-Dorg.gradle.jvmargs");
        }

        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.directory(projectDir);
        if (daemon) {
            String orgGradleJvmArgs = jvmArgs.isEmpty()
                ? ""
                : " \"-Dorg.gradle.jvmargs=" + gradleOpts + "\"";
            builder.environment().put("GRADLE_OPTS", "-Xmx128m -Xms128m -XX:+HeapDumpOnOutOfMemoryError" + orgGradleJvmArgs);
        } else {
            Logging.detailed().println("GRADLE_OPTS: " + gradleOpts);
            builder.environment().put("GRADLE_OPTS", gradleOpts);
        }
        Logging.detailed().println("JAVA_HOME: " + javaHome.getAbsolutePath());
        builder.environment().put("JAVA_HOME", javaHome.getAbsolutePath());
        builder.redirectErrorStream(true);
        try {
            if (buildLog == null) {
                new CommandExec().run(builder);
            } else {
                new CommandExec().runAndCollectOutput(buildLog, builder);
            }
        } catch (Exception e) {
            System.out.println();
            System.out.println("ERROR: failed to run build. See log file for details.");
            System.out.println();
            throw new RuntimeException("Build failed.", e);
        }
    }

    private static String quoteJvmArguments(boolean forSystemProperty, List<String> jvmArgs) {
        char quotes = forSystemProperty ? '\'' : '"';
        return jvmArgs.stream()
            .peek(arg -> {
                if (arg.contains("\"") || arg.contains("'")) {
                    throw new IllegalArgumentException("jvmArgs must not contain quotes, but this argument does: " + arg);
                }
            })
            .map(arg -> quotes + arg + quotes)
            .collect(Collectors.joining(" "));
    }
}
