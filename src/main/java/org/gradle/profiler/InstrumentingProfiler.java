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
 * <p>The profiler may support starting recording multiple times for a given JVM. The implementation should indicate this by overriding {@link #canRestartRecording()}.</p>
 */
public abstract class InstrumentingProfiler extends Profiler {
    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        validate(settings);
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
        SnapshotCapturingProfilerController controller = doNewController(settings);
        if (settings.getScenario().getInvoker() == Invoker.CliNoDaemon) {
            // Daemon will start recording and create snapshot on exit, so just start and end session
            return new SessionOnlyController(pid, controller);
        }
        if (settings.getScenario().getInvoker().isReuseDaemon()) {
            // Daemon will do nothing, so start and stop recording. Capture snapshot at end of session
            return new CaptureSnapshotOnSessionEndController(pid, controller);
        }
        // Daemon will start recording, so ignore request to start recording. Capture snapshot when stopping recording
        return new RecordingAlreadyStartedController(pid, controller);
    }

    private void validate(ScenarioSettings settings) {
        if (settings.getScenario().getBuildCount() > 1 && !canRestartRecording() && settings.getScenario().getCleanupAction().isDoesSomething()) {
            throw new IllegalArgumentException("Profiler " + toString() + " does not support profiling multiple iterations with cleanup steps in between.");
        }
        if (settings.getScenario().getBuildCount() > 1 && !settings.getScenario().getInvoker().isReuseDaemon()) {
            throw new IllegalArgumentException("Profiler " + toString() + " does not support profiling multiple daemons.");
        }
    }

    /**
     * Can this profiler implementation restart recording, for the same JVM?
     */
    protected boolean canRestartRecording() {
        return false;
    }

    /**
     * Creates JVM args to instrument that JVM with the given capabilities enabled.
     */
    protected abstract JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit);

    protected abstract SnapshotCapturingProfilerController doNewController(ScenarioSettings settings);

    public interface SnapshotCapturingProfilerController {
        void startRecording(String pid) throws IOException, InterruptedException;

        void stopRecording(String pid) throws IOException, InterruptedException;

        /**
         * Capture snapshot, if not already performed on stop.
         */
        void captureSnapshot(String pid) throws IOException, InterruptedException;

        void stopSession() throws IOException, InterruptedException;
    }

    private static class DelegatingController implements ProfilerController {
        protected final String pid;
        protected final SnapshotCapturingProfilerController controller;

        DelegatingController(String pid, SnapshotCapturingProfilerController controller) {
            this.pid = pid;
            this.controller = controller;
        }

        @Override
        public void startSession() throws IOException, InterruptedException {
        }

        @Override
        public void startRecording() throws IOException, InterruptedException {
            controller.startRecording(pid);
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

    private static class CaptureSnapshotOnSessionEndController extends DelegatingController {
        private String mostRecentPid;

        public CaptureSnapshotOnSessionEndController(String pid, SnapshotCapturingProfilerController controller) {
            super(pid, controller);
        }

        @Override
        public void stopRecording(String pid) throws IOException, InterruptedException {
            super.stopRecording(pid);
            mostRecentPid = pid;
        }

        @Override
        public void stopSession() throws IOException, InterruptedException {
            controller.captureSnapshot(mostRecentPid);
            controller.stopSession();
        }
    }

    private static class RecordingAlreadyStartedController extends DelegatingController {
        RecordingAlreadyStartedController(String pid, SnapshotCapturingProfilerController controller) {
            super(pid, controller);
        }

        @Override
        public void startRecording() throws IOException, InterruptedException {
            // Ignore
        }

        @Override
        public void stopRecording(String pid) throws IOException, InterruptedException {
            controller.stopRecording(pid);
            controller.captureSnapshot(pid);
        }
    }

    private static class SessionOnlyController extends DelegatingController {
        SessionOnlyController(String pid, SnapshotCapturingProfilerController controller) {
            super(pid, controller);
        }

        @Override
        public void startRecording() throws IOException, InterruptedException {
        }

        @Override
        public void stopRecording(String pid) throws IOException, InterruptedException {
        }
    }
}
