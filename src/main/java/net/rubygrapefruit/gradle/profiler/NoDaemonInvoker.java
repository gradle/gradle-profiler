package net.rubygrapefruit.gradle.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NoDaemonInvoker extends BuildInvoker {
    private final GradleVersion gradleVersion;

    public NoDaemonInvoker(GradleVersion gradleVersion, List<String> jvmArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        super(jvmArgs, pidInstrumentation, resultsConsumer);
        this.gradleVersion = gradleVersion;
    }

    @Override
    protected void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(new File(gradleVersion.getGradleHome(), "bin/gradle").getAbsolutePath());
        commandLine.addAll(gradleArgs);
        commandLine.add("--no-daemon");
        commandLine.addAll(tasks);
        String gradleOpts = jvmArgs.stream().collect(Collectors.joining(" "));
        System.out.println("Running " + commandLine);
        System.out.println("GRADLE_OPTS=" + gradleOpts);
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.environment().put("GRADLE_OPTS", gradleOpts);
        builder.redirectErrorStream();
        try {
            int exitCode = builder.start().waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Gradle command completed with non-zero exit code.");
            }
        } catch (Exception e) {
            System.out.println();
            System.out.println("ERROR: failed to run build. See log file for details.");
            System.out.println();
            throw new RuntimeException("Build failed.", e);
        }
    }
}
