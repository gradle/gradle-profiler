package org.gradle.profiler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.gradle.profiler.Logging.startOperation;

public class BuildActionInvoker {
    private final List<String> jvmArgs;
    private final List<String> gradleArgs;
    private final PidInstrumentation pidInstrumentation;
    private final Consumer<BuildInvocationResult> resultsConsumer;
    private final BuildInvoker buildInvoker;

    public BuildActionInvoker(List<String> jvmArgs, List<String> gradleArgs, BuildInvoker buildInvoker, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        this.jvmArgs = jvmArgs;
        this.gradleArgs = gradleArgs;
        this.buildInvoker = buildInvoker;
        this.pidInstrumentation = pidInstrumentation;
        this.resultsConsumer = resultsConsumer;
    }

    public BuildInvocationResult runBuild(Phase phase, int buildNumber, BuildStep buildStep, List<String> tasks, BuildAction buildAction) {
        String displayName = phase.displayBuildNumber(buildNumber);
        startOperation("Running " + displayName + " with " + buildStep.name().toLowerCase() + " tasks " + tasks);

        List<String> jvmArgs = new ArrayList<>(this.jvmArgs);
        jvmArgs.add("-Dorg.gradle.profiler.phase=" + phase);
        jvmArgs.add("-Dorg.gradle.profiler.number=" + buildNumber);
        jvmArgs.add("-Dorg.gradle.profiler.step=" + buildStep);

        Timer timer = new Timer();
        buildAction.run(buildInvoker, tasks, gradleArgs, jvmArgs);
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        Logging.detailed().println("Used daemon with pid " + pid);
        Main.printExecutionTime(executionTime);

        BuildInvocationResult results = new BuildInvocationResult(displayName, executionTime, pid);
        resultsConsumer.accept(results);
        return results;
    }

    public BuildActionInvoker notInstrumented() {
        return copy(jvmArgs, gradleArgs, buildInvocationResult -> { });
    }

    public BuildActionInvoker withJvmArgs(List<String> jvmArgs) {
        if (jvmArgs.equals(this.jvmArgs)) {
            return this;
        }
        return copy(jvmArgs, gradleArgs, resultsConsumer);
    }

    public BuildActionInvoker withGradleArgs(List<String> gradleArgs) {
        if (gradleArgs.equals(this.gradleArgs)) {
            return this;
        }
        return copy(jvmArgs, gradleArgs, resultsConsumer);
    }

    private BuildActionInvoker copy(List<String> jvmArgs, List<String> gradleArgs, Consumer<BuildInvocationResult> resultsConsumer) {
        return new BuildActionInvoker(jvmArgs, gradleArgs, buildInvoker, pidInstrumentation, resultsConsumer);
    }
}
