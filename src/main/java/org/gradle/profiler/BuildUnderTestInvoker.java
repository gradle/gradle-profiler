package org.gradle.profiler;

import org.gradle.profiler.buildops.BuildOperationInstrumentation;
import org.gradle.profiler.instrument.PidInstrumentation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a single invocation of a Gradle build and collects the result.
 */
public class BuildUnderTestInvoker {
    private final List<String> jvmArgs;
    private final List<String> gradleArgs;
    private final PidInstrumentation pidInstrumentation;
    private final BuildOperationInstrumentation buildOperationInstrumentation;
    private final GradleInvoker buildInvoker;

    public BuildUnderTestInvoker(List<String> jvmArgs, List<String> gradleArgs, GradleInvoker buildInvoker, PidInstrumentation pidInstrumentation, BuildOperationInstrumentation buildOperationInstrumentation) {
        this.jvmArgs = jvmArgs;
        this.gradleArgs = gradleArgs;
        this.buildInvoker = buildInvoker;
        this.pidInstrumentation = pidInstrumentation;
        this.buildOperationInstrumentation = buildOperationInstrumentation;
    }

    /**
     * Runs a single invocation of a Gradle build.
     */
    public GradleBuildInvocationResult runBuild(BuildContext buildContext, BuildStep buildStep, BuildAction buildAction) {
        String displayName = buildContext.getDisplayName();

        List<String> jvmArgs = new ArrayList<>(this.jvmArgs);
        jvmArgs.add("-Dorg.gradle.profiler.phase=" + buildContext.getPhase());
        jvmArgs.add("-Dorg.gradle.profiler.number=" + buildContext.getIteration());
        jvmArgs.add("-Dorg.gradle.profiler.step=" + buildStep);

        Timer timer = new Timer();
        buildAction.run(buildInvoker, gradleArgs, jvmArgs);
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        Logging.detailed().println("Used daemon with pid " + pid);

        Optional<Duration> timeToTaskExecution = buildOperationInstrumentation.getTimeToTaskExecution();

        Map<String, Duration> cumulativeBuildOperationTimes = buildOperationInstrumentation.getCumulativeBuildOperationTimes();
        cumulativeBuildOperationTimes.forEach((opName, duration) -> {
            Logging.detailed().println(String.format("Cumulative build operation time %s ms for %s", duration.toMillis(), opName));
        });
        Logging.detailed().println("Time to task execution " + timeToTaskExecution.map(duration -> duration.toMillis() + " ms").orElse(""));

        return new GradleBuildInvocationResult(displayName, executionTime, timeToTaskExecution.orElse(null), cumulativeBuildOperationTimes, pid);
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
        return new BuildUnderTestInvoker(jvmArgs, gradleArgs, buildInvoker, pidInstrumentation, buildOperationInstrumentation);
    }
}
