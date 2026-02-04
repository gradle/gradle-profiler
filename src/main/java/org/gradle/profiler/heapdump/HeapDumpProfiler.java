package org.gradle.profiler.heapdump;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.ScenarioSettings;

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
            try {
                File agentJar = findAgentJar();
                File outputDir = settings.profilerOutputLocationFor("").getParentFile();

                // Build agent argument: <outputDir>;<strategy1>,<strategy2>
                String agentArg = outputDir.getAbsolutePath() + ";" + String.join(",", strategies);

                jvmArgs.add("-javaagent:" + agentJar.getAbsolutePath() + "=" + agentArg);
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException("Failed to locate heap-dump-agent JAR", e);
            }
        }

        private File findAgentJar() throws IOException, URISyntaxException {
            // The agent JAR is packaged in META-INF/jars/ within the gradle-profiler JAR
            File resourcesDir = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            File metaInfJars = new File(resourcesDir, "META-INF/jars");

            // When running from JAR, resources are in the JAR itself
            // When running from IDE/tests, they're in build/resources/main
            if (!metaInfJars.exists()) {
                // Try build/resources/main/META-INF/jars
                File buildDir = resourcesDir.getParentFile(); // build/classes/java/main -> build/classes/java
                if (buildDir != null) {
                    buildDir = buildDir.getParentFile(); // build/classes/java -> build/classes
                    if (buildDir != null) {
                        buildDir = buildDir.getParentFile(); // build/classes -> build
                        if (buildDir != null) {
                            metaInfJars = new File(buildDir, "resources/main/META-INF/jars");
                        }
                    }
                }
            }

            File agentJar = new File(metaInfJars, "heap-dump-agent.jar");
            if (!agentJar.exists()) {
                throw new IOException("Could not find heap-dump-agent.jar at: " + agentJar.getAbsolutePath());
            }

            return agentJar;
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
