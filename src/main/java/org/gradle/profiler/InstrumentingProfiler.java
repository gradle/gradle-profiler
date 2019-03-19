package org.gradle.profiler;

import java.io.IOException;

/**
 * A profiler that instruments a JVM using JVM args, and can:
 *
 * <ul>
 *     <li>Start recording on JVM start, using JVM args</li>
 *     <li>Capture snapshot on JVM exit, using JVM args</li>
 *     <li>Use some communication mechanism to start recording in an instrumented JVM that is currently running.</li>
 *     <li>Use some communication mechanism to pause recording in an instrumented JVM that is currently running.</li>
 *     <li>Use some communication mechanism to capture a snapshot from an instrumented JVM that is currently running.</li>
 * </ul>
 */
public abstract class InstrumentingProfiler extends Profiler {
    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        if (!settings.getScenario().getInvoker().isReuseDaemon()) {
            // When the daemon is not reused, there is no need to instrument the warm ups
            return JvmArgsCalculator.DEFAULT;
        }
        // Instrument the daemon but do not start recording yet
        return jvmArgsWithInstrumentation(settings, false, false);
    }

    @Override
    public JvmArgsCalculator newInstrumentedBuildsJvmArgsCalculator(ScenarioSettings settings) {
        if (settings.getScenario().getInvoker().isReuseDaemon()) {
            // Is already instrumented
            return JvmArgsCalculator.DEFAULT;
        }
        // Start with recording enabled, and capture snapshot on exit when not using daemon
        boolean captureSnapshotOnExit = settings.getScenario().getInvoker() == Invoker.CliNoDaemon;
        return jvmArgsWithInstrumentation(settings, true, captureSnapshotOnExit);
    }

    @Override
    public ProfilerController newController(String pid, ScenarioSettings settings) {
        if (settings.getScenario().getInvoker() == Invoker.CliNoDaemon) {
            // Nothing to control
            return ProfilerController.EMPTY;
        }
        ProfilerController controller = doNewController(pid, settings);
        if (settings.getScenario().getInvoker().isReuseDaemon()) {
            return controller;
        }
        // Daemon will start recording
        return new ProfilerController() {
            @Override
            public void startSession() throws IOException, InterruptedException {
                controller.startSession();
            }

            @Override
            public void startRecording() throws IOException, InterruptedException {
                // Ignore
            }

            @Override
            public void stopRecording(String pid) throws IOException, InterruptedException {
                controller.stopRecording(pid);
            }

            @Override
            public void stopSession() throws IOException, InterruptedException {
                controller.stopSession();
            }
        };
    }

    protected abstract JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit);

    protected abstract ProfilerController doNewController(String pid, ScenarioSettings settings);
}
