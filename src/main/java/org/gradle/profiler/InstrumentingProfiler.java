package org.gradle.profiler;

import java.io.IOException;
import java.util.function.Consumer;

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
 * <p>The profiler may support starting recording multiple times for a given JVM. The implementation should indicate this by overriding {@link #canRestartRecording(ScenarioSettings)}.</p>
 */
public abstract class InstrumentingProfiler extends Profiler {
    /**
     * Calculates the JVM args for all builds, including warm-ups.
     *
     * <p>When the daemon will not be reused, this does nothing as there is no need to instrument the warm-up
     * builds in this case.
     *
     * <p>When the daemon will be reused for all builds, then instrument the daemon but do not start recording or capture snapshots yet.
     * Recording and capture will be enabled later when the measured builds run.
     */
    @Override
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        if (!settings.getScenario().getInvoker().isReuseDaemon()) {
            return JvmArgsCalculator.DEFAULT;
        }
        return jvmArgsWithInstrumentation(settings, false, false);
    }

    /**
     * Calculates the JVM args for measured builds.
     *
     * <p>When the daemon will be reused for all builds, this does nothing as the daemon is already instrumented (above).</p>
     *
     * <p>When using a cold daemon, start the JVM with recording enabled but do not capture snapshots yet.</p>
     *
     * <p>When using no daemon, start the JVM with recording enabled and capture a snapshot when the JVM exits.</p>
     */
    @Override
    public JvmArgsCalculator newInstrumentedBuildsJvmArgsCalculator(ScenarioSettings settings) {
        if (settings.getScenario().getInvoker().isReuseDaemon()) {
            return JvmArgsCalculator.DEFAULT;
        }
        boolean captureSnapshotOnExit = settings.getScenario().getInvoker().isDoesNotUseDaemon();
        return jvmArgsWithInstrumentation(settings, true, captureSnapshotOnExit);
    }

    /**
     * Creates a controller for this profiler.
     *
     * <p>When the daemon will be reused for all builds, create a controller that starts and stops recording when
     * requested, and that captures a snapshot at the end of the session.</p>
     *
     * <p>When using a cold daemon, create a controller that stops recording and captures a snapshot when requested,
     * but does not start recording as this is already enabled when the JVM starts.</p>
     *
     * <p>When using no daemon, return a controller that finishes the session only, as recording and snapshot capture
     * are already enabled when the JVM starts.</p>
     */
    @Override
    public ProfilerController newController(String pid, ScenarioSettings settings) {
        SnapshotCapturingProfilerController controller = doNewController(settings);
        if (settings.getScenario().getInvoker().isDoesNotUseDaemon()) {
            return new SessionOnlyController(pid, controller);
        }
        if (settings.getScenario().getInvoker().isReuseDaemon()) {
            return new CaptureSnapshotOnSessionEndController(pid, controller);
        }
        return new RecordingAlreadyStartedController(pid, controller);
    }

    @Override
    public void validate(ScenarioSettings settings, Consumer<String> reporter) {
        validateMultipleIterationsWithCleanupAction(settings, reporter);
        validateMultipleDaemons(settings, reporter);
    }

    protected void validateMultipleIterationsWithCleanupAction(ScenarioSettings settings, Consumer<String> reporter) {
        GradleScenarioDefinition scenario = settings.getScenario();
        if (scenario.getBuildCount() > 1 && !canRestartRecording(settings) && scenario.getCleanupAction().isDoesSomething()) {
            reporter.accept("Profiler " + toString() + " does not support profiling multiple iterations with cleanup steps in between.");
        }
    }

    protected void validateMultipleDaemons(ScenarioSettings settings, Consumer<String> reporter) {
        GradleScenarioDefinition scenario = settings.getScenario();
        if (scenario.getBuildCount() > 1 && !scenario.getInvoker().isReuseDaemon()) {
            reporter.accept("Profiler " + toString() + " does not support profiling multiple daemons.");
        }
    }

    /**
     * Can this profiler implementation restart recording, for the same JVM?
     */
    protected boolean canRestartRecording(ScenarioSettings settings) {
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
