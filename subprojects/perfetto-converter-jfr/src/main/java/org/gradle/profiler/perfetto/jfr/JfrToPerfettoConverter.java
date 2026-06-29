package org.gradle.profiler.perfetto.jfr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import jdk.jfr.consumer.RecordingFile;
import org.gradle.profiler.perfetto.jfr.processor.GradleBuildOperationProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrEventProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrCpuUsageProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrGcProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrHeapSummaryProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrPidDiscoveryProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrSampleProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrThreadLifetimeProcessor;
import org.gradle.profiler.perfetto.jfr.processor.JfrThreadStateProcessor;

/**
 * Coordinates the end-to-end translation of a JFR recording into a Perfetto trace.
 *
 * <p>It discovers recording metadata, then runs the event processors that emit the converted trace.
 */
@SuppressWarnings("RedundantExplicitVariableType")
public final class JfrToPerfettoConverter {
    private JfrToPerfettoConverter() {
    }

    public static void convert(File jfrInput, File perfettoOutput) {
        // Getting the PID from the JFR.
        JfrPidDiscoveryProcessor pidProcessor = new JfrPidDiscoveryProcessor(jfrInput.toPath());
        var plan = runProcessor(jfrInput.toPath(), pidProcessor, "Failed to discover metadata from ");

        // Setting up the output infrastructure.
        try (PerfettoTraceWriter writer = new PerfettoTraceWriter(perfettoOutput.toPath())) {
            // Building the conversion session and emitting the initial clock snapshot.
            PerfettoIdProvider idProvider = new PerfettoIdProvider();
            PerfettoTraceEmitter emitter = new PerfettoTraceEmitter(writer);
            emitter.emitPrimaryRealtimeClockSnapshot(plan.startTimestampNs());
            ConverterSession context = new ConverterSession(plan.pid(), idProvider, emitter);

            // Collecting and emitting thread information.
            JfrThreadLifetimeProcessor threadProcessor = new JfrThreadLifetimeProcessor();
            runProcessor(plan.input(), context, threadProcessor, "Failed to emit thread lifetime data from ");

            // Emitting build operation output.
            GradleBuildOperationProcessor buildOperationProcessor = new GradleBuildOperationProcessor();
            runProcessor(plan.input(), context, buildOperationProcessor, "Failed to emit build operations from ");

            // Emitting CPU counters.
            JfrCpuUsageProcessor cpuProcessor = new JfrCpuUsageProcessor();
            runProcessor(plan.input(), context, cpuProcessor, "Failed to emit CPU usage from ");

            // Emitting GC slices.
            JfrGcProcessor gcProcessor = new JfrGcProcessor();
            runProcessor(plan.input(), context, gcProcessor, "Failed to emit GC data from ");

            // Emitting heap counters.
            JfrHeapSummaryProcessor heapProcessor = new JfrHeapSummaryProcessor();
            runProcessor(plan.input(), context, heapProcessor, "Failed to emit heap summary data from ");

            // Emitting sampled stack traces.
            JfrSampleProcessor sampleProcessor = new JfrSampleProcessor();
            runProcessor(plan.input(), context, sampleProcessor, "Failed to emit samples from ");

            // Emitting thread state slices.
            JfrThreadStateProcessor threadStateProcessor = new JfrThreadStateProcessor();
            runProcessor(plan.input(), context, threadStateProcessor, "Failed to emit thread state data from ");
        } catch (IOException ex) {
            throw new RuntimeException("Failed to convert JFR recording " + jfrInput.getAbsolutePath(), ex);
        }
    }

    private static <R> R runProcessor(Path input, JfrEventProcessor<R> processor, String failurePrefix) {
        try (RecordingFile recordingFile = new RecordingFile(input)) {
            while (recordingFile.hasMoreEvents()) {
                if (processor.process(recordingFile.readEvent(), null)) {
                    break;
                }
            }
            return processor.finish(null)
                .orElseThrow(() -> new IllegalStateException("Processor did not produce a result for " + input));
        } catch (IOException ex) {
            throw new RuntimeException(failurePrefix + input, ex);
        }
    }

    private static void runProcessor(Path input, ConverterSession context, JfrEventProcessor<?> processor, String failurePrefix) {
        try (RecordingFile recordingFile = new RecordingFile(input)) {
            while (recordingFile.hasMoreEvents()) {
                if (processor.process(recordingFile.readEvent(), context)) {
                    break;
                }
            }
            processor.finish(context);
        } catch (IOException ex) {
            throw new RuntimeException(failurePrefix + input, ex);
        }
    }
}
