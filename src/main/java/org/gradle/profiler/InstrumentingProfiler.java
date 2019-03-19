package org.gradle.profiler;

import java.io.IOException;

/**
 * A profiler that can:
 *
 * <ul>
 * <li>Instrument a JVM using JVM args</li>
 * <li>Start recording on JVM start, using JVM args</li>
 * <li>Capture snapshot on JVM exit, using JVM args</li>
 * <li>Use some communication mechanism to start recording in an instrumented JVM that is currently running.</li>
 * <li>Use some communication mechanism to pause recording in an instrumented JVM that is currently running.</li>
 * <li>Use some communication mechanism to capture a snapshot from an instrumented JVM that is currently running.</li>
 * </ul>
 *
 * <p>The profiler may or may not support starting recording multiple times for a given JVM.</p>
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
        ProfilerController controller = doNewController(pid, settings);
        if (settings.getScenario().getInvoker() == Invoker.CliNoDaemon) {
            // Daemon will start recording and create snapshot on exit, so just start and end session
            return new SessionOnlyController(controller);
        }
        if (settings.getScenario().getInvoker().isReuseDaemon()) {
            return controller;
        }
        // Daemon will start recording, so ignore request to start recording
        return new IgnoreStartController(controller);
    }

    /**
     * Creates JVM args to instrument that JVM with the given capabilities enabled.
     */
    protected abstract JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit);

    protected abstract ProfilerController doNewController(String pid, ScenarioSettings settings);

    private static class DelegatingController implements ProfilerController {
        private final ProfilerController controller;

        public DelegatingController(ProfilerController controller) {
            this.controller = controller;
        }

        @Override
        public void startSession() throws IOException, InterruptedException {
            controller.startSession();
        }

        @Override
        public void startRecording() throws IOException, InterruptedException {
            controller.startRecording();
        }

        @Override
        public void stopRecording(String pid) throws IOException, InterruptedException {
            controller.stopRecording(pid);
        }

        @Override
        public void stopSession() throws IOException, InterruptedException {
            controller.stopSession();
        }
    }

    private static class IgnoreStartController extends DelegatingController {
        IgnoreStartController(ProfilerController controller) {
            super(controller);
        }

        @Override
        public void startRecording() throws IOException, InterruptedException {
            // Ignore
        }
    }

    private static class SessionOnlyController extends DelegatingController {
        SessionOnlyController(ProfilerController controller) {
            super(controller);
        }

        @Override
        public void startRecording() throws IOException, InterruptedException {
        }

        @Override
        public void stopRecording(String pid) throws IOException, InterruptedException {
        }
    }
}
