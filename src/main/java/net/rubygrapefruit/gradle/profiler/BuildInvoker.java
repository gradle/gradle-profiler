package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

class BuildInvoker {
    private final ProjectConnection projectConnection;
    private final List<String> jvmArgs;
    private final List<?> tasks;
    private final PidInstrumentation pidInstrumentation;

    public BuildInvoker(ProjectConnection projectConnection, List<?> tasks, List<String> jvmArgs, PidInstrumentation pidInstrumentation) {
        this.projectConnection = projectConnection;
        this.tasks = tasks;
        this.jvmArgs = jvmArgs;
        this.pidInstrumentation = pidInstrumentation;
    }

    public BuildResults runBuild() throws IOException {
        Timer timer = new Timer();
        BuildLauncher build = projectConnection.newBuild();
        build.forTasks(tasks.toArray(new String[0]));
        build.withArguments(pidInstrumentation.getArgs());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        build.setStandardOutput(outputStream);
        build.setStandardError(outputStream);
        build.setJvmArguments(jvmArgs);
        try {
            build.run();
        } catch (GradleConnectionException e) {
            System.out.println();
            System.out.println("ERROR: failed to run build.");
            System.out.println();
            System.out.append(new String(outputStream.toByteArray()));
            System.out.println();
            throw e;
        }
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        System.out.println("Used daemon with pid " + pid);
        System.out.println("Execution time " + executionTime.toMillis() + "ms");

        return new BuildResults(executionTime, pid);
    }
}
