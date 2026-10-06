package org.gradle.profiler.gradle;

import org.gradle.profiler.*;
import org.gradle.profiler.instrument.BuildFinishedInstrumentation;

import javax.annotation.Nullable;
import java.io.IOException;

public class RecordingBuildStepAction implements BuildStepAction<GradleBuildInvocationResult> {
    private final BuildStepAction<GradleBuildInvocationResult> action;
    private final BuildStepAction<?> cleanupAction;
    private final GradleScenarioDefinition scenario;
    private final ProfilerController controller;
    @Nullable
    private final BuildFinishedInstrumentation buildFinished;

    /**
     * @param buildFinished when not null, recording is stopped when the build process notifies the end of the build, rather than after the build.
     */
    public RecordingBuildStepAction(BuildStepAction<GradleBuildInvocationResult> action,
                                    BuildStepAction<?> cleanupAction,
                                    GradleScenarioDefinition scenario,
                                    ProfilerController controller,
                                    @Nullable BuildFinishedInstrumentation buildFinished) {
        this.action = action;
        this.cleanupAction = cleanupAction;
        this.scenario = scenario;
        this.controller = controller;
        this.buildFinished = buildFinished;
    }

    @Override
    public boolean isDoesSomething() {
        return action.isDoesSomething();
    }

    @Override
    public GradleBuildInvocationResult run(BuildContext buildContext, BuildStep buildStep) {
        if ((buildContext.getIteration() == 1 || cleanupAction.isDoesSomething())) {
            try {
                controller.startRecording();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        boolean stopRecording = buildContext.getIteration() == scenario.getBuildCount() || cleanupAction.isDoesSomething();
        if (buildFinished != null) {
            return runAndStopRecordingWhenBuildFinishes(buildContext, buildStep, stopRecording, buildFinished);
        }

        GradleBuildInvocationResult result = action.run(buildContext, buildStep);

        if (stopRecording) {
            try {
                controller.stopRecording(result.getDaemonPid());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return result;
    }

    private GradleBuildInvocationResult runAndStopRecordingWhenBuildFinishes(BuildContext buildContext, BuildStep buildStep, boolean stopRecording, BuildFinishedInstrumentation buildFinished) {
        // The build process waits for every notification to be handled, so always handle it
        buildFinished.onNextBuildFinished(pid -> {
            if (stopRecording) {
                controller.stopRecording(pid);
            }
        });
        GradleBuildInvocationResult result;
        try {
            result = action.run(buildContext, buildStep);
        } catch (RuntimeException e) {
            buildFinished.buildFailed(e);
            throw e;
        }
        try {
            buildFinished.buildCompleted();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
