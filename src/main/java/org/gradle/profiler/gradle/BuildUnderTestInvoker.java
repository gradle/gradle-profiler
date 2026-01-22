package org.gradle.profiler.gradle;

import org.gradle.profiler.*;
import org.gradle.profiler.buildops.BuildOperationExecutionData;
import org.gradle.profiler.buildops.BuildOperationInstrumentation;
import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.result.BuildActionResult;

import java.time.Duration;
import java.util.*;

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

    BuildStepAction<GradleBuildInvocationResult> NO_OP = new BuildStepAction<GradleBuildInvocationResult>() {
        @Override
        public boolean isDoesSomething() {
            return false;
        }

        @Override
        public GradleBuildInvocationResult run(BuildContext buildContext, BuildStep buildStep) {
            return null;
        }
    };

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
            return NO_OP;
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
            jvmArgs.add("-Dorg.gradle.profiler.phase.display.name=" + buildContext.getPhase().getDisplayName());
            jvmArgs.add("-Dorg.gradle.profiler.number=" + buildContext.getIteration());
            jvmArgs.add("-Dorg.gradle.profiler.step=" + buildStep);

            BuildActionResult buildActionResult = action.run(gradleClient, gradleArgs, jvmArgs);

            String pid = pidInstrumentation.getPidForLastBuild();
            Logging.detailed().printf("Used daemon with pid %s%n", pid);

            Optional<Duration> garbageCollectionTime = buildOperationInstrumentation.getTotalGarbageCollectionTime()
                .map(currentTotal -> {
                    Duration previousTotal = previousGcTimes.getOrDefault(pid, Duration.ZERO);
                    previousGcTimes.put(pid, currentTotal);
                    return currentTotal.minus(previousTotal);
                });
            Optional<Long> localBuildCacheSize = buildOperationInstrumentation.getLocalBuildCacheSize();
            Optional<Duration> timeToTaskExecution = buildOperationInstrumentation.getTimeToTaskExecution();

            Map<String, BuildOperationExecutionData> totalExecutionData = buildOperationInstrumentation.getTotalBuildOperationExecutionData();
            totalExecutionData.forEach((opName, duration) -> {
                Logging.detailed().printf(
                    "Total build operation time %s ms (%s occurrences) for %s%n",
                    duration.getValue(), duration.getTotalCount(), opName);
            });
            garbageCollectionTime.ifPresent(duration -> Logging.detailed().printf("Total GC time: %d ms%n", duration.toMillis()));
            timeToTaskExecution.ifPresent(duration -> Logging.detailed().printf("Time to task execution %d ms%n", duration.toMillis()));

            return new GradleBuildInvocationResult(
                buildContext,
                buildActionResult,
                garbageCollectionTime.orElse(null),
                localBuildCacheSize.orElse(null),
                timeToTaskExecution.orElse(null),
                totalExecutionData,
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
