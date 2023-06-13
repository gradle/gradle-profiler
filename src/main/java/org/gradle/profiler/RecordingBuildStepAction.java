package org.gradle.profiler;

import java.io.IOException;

import static org.gradle.profiler.BuildStep.BUILD;

public class RecordingBuildStepAction implements BuildStepAction<GradleBuildInvocationResult> {
    private final BuildStepAction<GradleBuildInvocationResult> action;
    private final BuildStepAction<?> cleanupAction;
    private final GradleScenarioDefinition scenario;
    private final ProfilerController controller;

    public RecordingBuildStepAction(BuildStepAction<GradleBuildInvocationResult> action,
                                    BuildStepAction<?> cleanupAction,
                                    GradleScenarioDefinition scenario,
                                    ProfilerController controller) {
        this.action = action;
        this.cleanupAction = cleanupAction;
        this.scenario = scenario;
        this.controller = controller;
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

        GradleBuildInvocationResult result = action.run(buildContext, buildStep);

        if ((buildContext.getIteration() == scenario.getBuildCount() || cleanupAction.isDoesSomething())) {
            try {
                controller.stopRecording(result.getDaemonPid());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return result;
    }
}
