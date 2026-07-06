package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.gradle.profiler.perfetto.jfr.PerfettoCallstackInterner;
import org.gradle.profiler.perfetto.jfr.ThreadIdentity;
import org.jspecify.annotations.Nullable;
import perfetto.protos.PerfSample;

/**
 * Converts sampled stack traces into Perfetto perf samples with interned callstacks.
 *
 * <p>Each sample carries a timebase count of 1, so Perfetto's weighted flamegraph mode aggregates
 * plain sample counts. JFR samples are point-in-time observations without a recorded sampling
 * period, so there is no duration to attribute to them; the sequence's perf sample defaults name
 * the timebase "samples" to make the unit explicit in the UI.
 *
 * <p>Consumes:
 * <ul>
 *   <li>{@code jdk.ExecutionSample}</li>
 *   <li>{@code jdk.NativeMethodSample}</li>
 * </ul>
 */
public final class JfrSampleProcessor implements JfrEventProcessor<Void> {
    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";
    private static final String NATIVE_METHOD_SAMPLE_EVENT = "jdk.NativeMethodSample";

    @Override
    public void start(@Nullable ConverterSession context) throws IOException {
        if (context != null) {
            context.emitter().emitPerfSampleDefaults();
        }
    }

    @Override
    public boolean process(RecordedEvent event, ConverterSession context) throws IOException {
        String eventName = event.getEventType().getName();
        if (!EXECUTION_SAMPLE_EVENT.equals(eventName) && !NATIVE_METHOD_SAMPLE_EVENT.equals(eventName)) {
            return false;
        }

        ThreadIdentity thread = resolveSampleThread(event);
        if (thread == null) {
            return false;
        }

        PerfettoCallstackInterner.InterningResult interningResult = context.interning().internCallstack(event.getStackTrace());
        if (interningResult.delta() != null) {
            context.emitter().emitInternedData(interningResult.delta());
        }
        if (interningResult.callstackIid() <= 0) {
            return false;
        }

        long timestampNs = context.toEpochNanos(event.getStartTime());
        context.emitter().emitPerfSample(timestampNs, PerfSample.newBuilder()
            .setPid(context.pid())
            .setTid(thread.tid())
            .setCallstackIid(interningResult.callstackIid())
            .setTimebaseCount(1L)
            .build());
        return false;
    }

    @Nullable
    private static ThreadIdentity resolveSampleThread(RecordedEvent event) {
        if (event.hasField("sampledThread")) {
            try {
                RecordedThread sampledThread = event.getThread("sampledThread");
                ThreadIdentity sampled = ThreadIdentity.from(sampledThread);
                if (sampled != null) {
                    return sampled;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to event thread
            }
        }
        return ThreadIdentity.from(event.getThread());
    }
}
