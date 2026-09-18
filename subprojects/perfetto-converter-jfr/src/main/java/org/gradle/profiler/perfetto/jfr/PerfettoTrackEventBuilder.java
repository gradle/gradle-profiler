package org.gradle.profiler.perfetto.jfr;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import perfetto.protos.DebugAnnotation;
import perfetto.protos.TrackEvent;

/**
 * Fluent builder for a single Perfetto track event, owning the timing and emission bookkeeping.
 *
 * <p>Created from {@link ConverterSession#trackEvent}, it captures the slice timing once (from a
 * {@link jdk.jfr.consumer.RecordedEvent} or explicit nanos), collects a name and debug annotations,
 * and emits as either a slice or a counter. Absent annotation values are skipped so callers don't
 * repeat null guards.
 */
public final class PerfettoTrackEventBuilder {
    private final ConverterSession session;
    private final long startNs;
    private final long endNs;
    private final TrackEvent.Builder event = TrackEvent.newBuilder();

    PerfettoTrackEventBuilder(ConverterSession session, long startNs, long endNs) {
        this.session = session;
        this.startNs = startNs;
        this.endNs = endNs;
    }

    public PerfettoTrackEventBuilder onTrack(long trackUuid) {
        event.setTrackUuid(trackUuid);
        return this;
    }

    public PerfettoTrackEventBuilder name(String name) {
        event.setName(name);
        return this;
    }

    public PerfettoTrackEventBuilder annotate(String name, long value) {
        event.addDebugAnnotations(DebugAnnotation.newBuilder().setName(name).setIntValue(value));
        return this;
    }

    public PerfettoTrackEventBuilder annotate(String name, boolean value) {
        event.addDebugAnnotations(DebugAnnotation.newBuilder().setName(name).setBoolValue(value));
        return this;
    }

    public PerfettoTrackEventBuilder annotateIfPresent(String name, @Nullable String value) {
        if (value != null) {
            event.addDebugAnnotations(DebugAnnotation.newBuilder().setName(name).setStringValue(value));
        }
        return this;
    }

    public void emitSlice() throws IOException {
        session.emitter().emitSlice(startNs, endNs, event);
    }

    public void emitCounter(double value) throws IOException {
        session.emitter().emitCounter(startNs, event.getTrackUuid(), value);
    }
}
