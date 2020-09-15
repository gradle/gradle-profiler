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

    public CliGradleClient(GradleBuildConfiguration gradleBuildConfiguration, File javaHome, File projectDir, boolean daemon) {
        this.gradleBuildConfiguration = gradleBuildConfiguration;
        this.javaHome = javaHome;
        this.projectDir = projectDir;
        this.daemon = daemon;
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
        String gradleOpts = jvmArgs.stream().map(arg -> '"' + arg + '"').collect(Collectors.joining(" "));

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
