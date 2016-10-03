package net.rubygrapefruit.gradle.profiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.rubygrapefruit.gradle.profiler.Logging.*;

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
        commandLine.add(new File(gradleVersion.getGradleHome(), "bin/gradle").getAbsolutePath());
        commandLine.addAll(gradleArgs);
        commandLine.add("--no-daemon");
        commandLine.addAll(tasks);
        String gradleOpts = jvmArgs.stream().collect(Collectors.joining(" "));
        detailed().println("Running command:");
        for (String arg : commandLine) {
            detailed().println("  " + arg);
        }
        detailed().println("JAVA_HOME: " + javaHome.getAbsolutePath());
        detailed().println("GRADLE_OPTS: " + gradleOpts);
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.directory(projectDir);
        builder.environment().put("GRADLE_OPTS", gradleOpts);
        builder.environment().put("JAVA_HOME", javaHome.getAbsolutePath());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            Thread drain = new Thread(){
                @Override
                public void run() {
                    InputStream inputStream = process.getInputStream();
                    byte[] buffer = new byte[1024];
                    try {
                        while (true) {
                            int nread = inputStream.read(buffer);
                            if (nread < 0) {
                                break;
                            }
                            detailed().write(buffer, 0, nread);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Could not read process output.");
                    }
                }
            };
            drain.start();
            int exitCode = process.waitFor();
            drain.join();
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
