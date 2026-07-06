package org.gradle.profiler.perfetto.jfr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jdk.jfr.consumer.RecordedEvent;
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
 * <p>It discovers recording metadata in a first pass, then streams the recording once through all
 * event processors that emit the converted trace.
 */
@SuppressWarnings("RedundantExplicitVariableType")
public final class JfrToPerfettoConverter {
    private JfrToPerfettoConverter() {
    }

    public static void convert(File jfrInput, File perfettoOutput) {
        // Getting the PID from the JFR.
        // This requires a separate pass, hence the separaton
        JfrPidDiscoveryProcessor pidProcessor = new JfrPidDiscoveryProcessor(jfrInput.toPath());
        var plan = runProcessor(jfrInput.toPath(), pidProcessor, "Failed to discover metadata from ");

        // Setting up the output infrastructure.
        try (PerfettoTraceWriter writer = new PerfettoTraceWriter(perfettoOutput.toPath())) {
            // Building the conversion session and emitting the initial clock snapshot.
            PerfettoIdProvider idProvider = new PerfettoIdProvider();
            PerfettoTraceEmitter emitter = new PerfettoTraceEmitter(writer);
            emitter.emitPrimaryRealtimeClockSnapshot(plan.startTimestampNs());
            ConverterSession context = new ConverterSession(plan.pid(), idProvider, emitter);

            // Streaming the recording once through all processors.
            List<JfrEventProcessor<?>> processors = List.of(
                new JfrThreadLifetimeProcessor(),
                new GradleBuildOperationProcessor(),
                new JfrCpuUsageProcessor(),
                new JfrGcProcessor(),
                new JfrHeapSummaryProcessor(),
                new JfrSampleProcessor(),
                new JfrThreadStateProcessor()
            );
            runProcessors(plan.input(), context, processors);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to convert JFR recording " + jfrInput.getAbsolutePath(), ex);
        }
    }

    private static void runProcessors(Path input, ConverterSession context, List<JfrEventProcessor<?>> processors) {
        try (RecordingFile recordingFile = new RecordingFile(input)) {
            for (JfrEventProcessor<?> processor : processors) {
                processor.start(context);
            }
            // Processors that report completion are dropped from the fan-out; finish() still runs for all.
            List<JfrEventProcessor<?>> active = new ArrayList<>(processors);
            while (recordingFile.hasMoreEvents() && !active.isEmpty()) {
                RecordedEvent event = recordingFile.readEvent();
                Iterator<JfrEventProcessor<?>> iterator = active.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().process(event, context)) {
                        iterator.remove();
                    }
                }
            }
            for (JfrEventProcessor<?> processor : processors) {
                processor.finish(context);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to emit trace data from " + input, ex);
        }
    }

    private static <R> R runProcessor(Path input, JfrEventProcessor<R> processor, String failurePrefix) {
        try (RecordingFile recordingFile = new RecordingFile(input)) {
            processor.start(null);
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

}
