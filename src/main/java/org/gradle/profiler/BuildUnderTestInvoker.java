package org.gradle.profiler;

import org.gradle.profiler.buildops.BuildOperationInstrumentation;
import org.gradle.profiler.instrument.PidInstrumentation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a single invocation of a Gradle build and collects the result.
 */
public class BuildUnderTestInvoker {
    private final List<String> jvmArgs;
    private final List<String> gradleArgs;
    private final GradleClient gradleClient;
    private final PidInstrumentation pidInstrumentation;
    private final BuildOperationInstrumentation buildOperationInstrumentation;
    private final Map<String, Duration> previousGcTimes = new HashMap<>();

    public BuildUnderTestInvoker(List<String> jvmArgs, List<String> gradleArgs, GradleClient gradleClient, PidInstrumentation pidInstrumentation, BuildOperationInstrumentation buildOperationInstrumentation) {
        this.jvmArgs = jvmArgs;
        this.gradleArgs = gradleArgs;
        this.gradleClient = gradleClient;
        this.pidInstrumentation = pidInstrumentation;
        this.buildOperationInstrumentation = buildOperationInstrumentation;
    }

    public BuildStepAction<GradleBuildInvocationResult> create(BuildAction action) {
        if (action.isDoesSomething()) {
            return new InvokeAndMeasureAction(action);
        } else {
            return BuildStepAction.NO_OP;
        }
    }

    private class InvokeAndMeasureAction implements BuildStepAction<GradleBuildInvocationResult> {
        private final BuildAction action;

        public InvokeAndMeasureAction(BuildAction action) {
            this.action = action;
        }

        @Override
        public boolean isDoesSomething() {
            return true;
        }

        @Override
        public GradleBuildInvocationResult run(BuildContext buildContext, BuildStep buildStep) {
            List<String> jvmArgs = new ArrayList<>(BuildUnderTestInvoker.this.jvmArgs);
            jvmArgs.add("-Dorg.gradle.profiler.phase=" + buildContext.getPhase());
            jvmArgs.add("-Dorg.gradle.profiler.number=" + buildContext.getIteration());
            jvmArgs.add("-Dorg.gradle.profiler.step=" + buildStep);

            Duration executionTime = action.run(gradleClient, gradleArgs, jvmArgs);

            String pid = pidInstrumentation.getPidForLastBuild();
            Logging.detailed().printf("Used daemon with pid %s%n", pid);

            Optional<Duration> garbageCollectionTime = buildOperationInstrumentation.getTotalGarbageCollectionTime()
                .map(currentTotal -> {
                    Duration previousTotal = previousGcTimes.getOrDefault(pid, Duration.ZERO);
                    previousGcTimes.put(pid, currentTotal);
                    return currentTotal.minus(previousTotal);
                });
            Optional<Duration> timeToTaskExecution = buildOperationInstrumentation.getTimeToTaskExecution();

            Map<String, Duration> cumulativeBuildOperationTimes = buildOperationInstrumentation.getCumulativeBuildOperationTimes();
            cumulativeBuildOperationTimes.forEach((opName, duration) -> {
                Logging.detailed().printf("Cumulative build operation time %s ms for %s%n", duration.toMillis(), opName);
            });
            garbageCollectionTime.ifPresent(duration -> Logging.detailed().printf("Total GC time: %d ms%n", duration.toMillis()));
            timeToTaskExecution.ifPresent(duration -> Logging.detailed().printf("Time to task execution %d ms%n", duration.toMillis()));

            return new GradleBuildInvocationResult(
                buildContext,
                executionTime,
                garbageCollectionTime.orElse(null),
                timeToTaskExecution.orElse(null),
                cumulativeBuildOperationTimes,
                pid);
        }
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
        return new BuildUnderTestInvoker(jvmArgs, gradleArgs, gradleClient, pidInstrumentation, buildOperationInstrumentation);
    }
}
