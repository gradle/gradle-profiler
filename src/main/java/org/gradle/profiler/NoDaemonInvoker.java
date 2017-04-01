package org.gradle.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NoDaemonInvoker extends CliInvoker {

    public NoDaemonInvoker(GradleVersion gradleVersion, File javaHome, File projectDir, List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        super(gradleVersion, javaHome, projectDir, jvmArgs, gradleArgs, pidInstrumentation, resultsConsumer);
    }

    @Override
    protected void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs) {
        List<String> withNoDaemon = new ArrayList<>();
        withNoDaemon.addAll(gradleArgs);
        withNoDaemon.add("--no-daemon");
        super.run(tasks, withNoDaemon, jvmArgs);
    }
}
