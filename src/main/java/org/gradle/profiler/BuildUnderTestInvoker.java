package org.gradle.profiler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class BuildUnderTestInvoker {
    private final List<String> jvmArgs;
    private final List<String> gradleArgs;
    private final PidInstrumentation pidInstrumentation;
    private final GradleInvoker buildInvoker;

    public BuildUnderTestInvoker(List<String> jvmArgs, List<String> gradleArgs, GradleInvoker buildInvoker, PidInstrumentation pidInstrumentation) {
        this.jvmArgs = jvmArgs;
        this.gradleArgs = gradleArgs;
        this.buildInvoker = buildInvoker;
        this.pidInstrumentation = pidInstrumentation;
    }

    /**
     * Runs a single invocation of a build.
     */
    public BuildInvocationResult runBuild(Phase phase, int buildNumber, BuildStep buildStep, BuildAction buildAction) {
        String displayName = phase.displayBuildNumber(buildNumber);

        List<String> jvmArgs = new ArrayList<>(this.jvmArgs);
        jvmArgs.add("-Dorg.gradle.profiler.phase=" + phase);
        jvmArgs.add("-Dorg.gradle.profiler.number=" + buildNumber);
        jvmArgs.add("-Dorg.gradle.profiler.step=" + buildStep);

        Timer timer = new Timer();
        buildAction.run(buildInvoker, gradleArgs, jvmArgs);
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        Logging.detailed().println("Used daemon with pid " + pid);

        return new BuildInvocationResult(displayName, executionTime, pid);
    }

    public BuildUnderTestInvoker withJvmArgs(List<String> jvmArgs) {
        if (jvmArgs.equals(this.jvmArgs)) {
            return this;
        }
        return copy(jvmArgs, gradleArgs);
    }

    public BuildUnderTestInvoker withGradleArgs(List<String> gradleArgs) {
        if (gradleArgs.equals(this.gradleArgs)) {
            return this;
        }
        return copy(jvmArgs, gradleArgs);
    }

    private BuildUnderTestInvoker copy(List<String> jvmArgs, List<String> gradleArgs) {
        return new BuildUnderTestInvoker(jvmArgs, gradleArgs, buildInvoker, pidInstrumentation);
    }
}
