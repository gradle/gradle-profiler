package org.gradle.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NoDaemonInvoker extends CliInvoker {

    public NoDaemonInvoker(GradleVersion gradleVersion, File javaHome, File projectDir, List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        super(gradleVersion, javaHome, projectDir, jvmArgs, withNoDaemonOption(gradleArgs), pidInstrumentation, resultsConsumer);
    }

    private static List<String> withNoDaemonOption(List<String> gradleArgs) {
        List<String> args = new ArrayList<>();
        args.addAll(gradleArgs);
        args.add("--no-daemon");
        return args;
    }
}
