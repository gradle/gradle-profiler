package org.gradle.profiler.heapdump;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.instrument.GradleInstrumentation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class HeapDumpProfiler extends InstrumentingProfiler {
    private final Set<String> heapDumpWhen;

    public HeapDumpProfiler(Set<String> heapDumpWhen) {
        this.heapDumpWhen = heapDumpWhen;
    }

    @Override
    public boolean requiresGradle() {
        return true;
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        // The agent handles everything automatically once loaded
        // We don't need conditional instrumentation based on startRecordingOnProcessStart/captureSnapshotOnProcessExit
        return new HeapDumpAgentJvmArgsCalculator(settings, heapDumpWhen);
    }

    @Override
    public SnapshotCapturingProfilerController newSnapshottingController(ScenarioSettings settings) {
        // The agent handles heap dumps automatically, no dynamic control needed
        return new NoOpController();
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        String fileName = resultFile.getName();
        if ((fileName.startsWith("gradle-config-end-") || fileName.startsWith("gradle-build-end-"))
            && fileName.endsWith(".hprof")) {
            consumer.accept(resultFile.getAbsolutePath());
        }
    }

    @Override
    public String toString() {
        return "heap-dump";
    }

    private static class HeapDumpAgentJvmArgsCalculator implements JvmArgsCalculator {
        private final ScenarioSettings settings;
        private final Set<String> strategies;

        HeapDumpAgentJvmArgsCalculator(ScenarioSettings settings, Set<String> strategies) {
            this.settings = settings;
            this.strategies = strategies;
        }

        @Override
        public void calculateJvmArgs(List<String> jvmArgs) {
            String agentJar = GradleInstrumentation.unpackPlugin("heap-dump-agent").getAbsolutePath();
            String runtimeJar = GradleInstrumentation.unpackPlugin("heap-dump-runtime").getAbsolutePath();
            String outputDir = settings.profilerOutputLocationFor("").getParentFile().getAbsolutePath();

            // Build agent argument: <runtimeJar>;<outputDir>;<strategy1>,<strategy2>
            String agentArg = runtimeJar + ";" + outputDir + ";" + String.join(",", strategies);
            jvmArgs.add("-javaagent:" + agentJar + "=" + agentArg);
        }
    }

    private static class NoOpController implements SnapshotCapturingProfilerController {
        @Override
        public void startRecording(String pid) throws IOException, InterruptedException {
            // No-op: agent handles everything automatically
        }

        @Override
        public void stopRecording(String pid) throws IOException, InterruptedException {
            // No-op: agent handles everything automatically
        }

        @Override
        public void captureSnapshot(String pid) throws IOException, InterruptedException {
            // No-op: agent handles everything automatically
        }

        @Override
        public void stopSession() throws IOException, InterruptedException {
            // No-op: agent handles everything automatically
        }
    }
}
