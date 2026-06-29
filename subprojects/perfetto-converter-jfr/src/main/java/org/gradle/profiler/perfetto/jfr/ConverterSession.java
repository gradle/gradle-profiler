package org.gradle.profiler.perfetto.jfr;

import java.io.IOException;
import java.time.Instant;
import jdk.jfr.consumer.RecordedEvent;

/**
 * Shared conversion infrastructure passed through processors while a recording is being translated.
 *
 * <p>It centralizes trace-writing helpers, track registries, encoding state, and summary counters.
 */
public final class ConverterSession {
    private final int pid;
    private final PerfettoTraceEmitter emitter;
    private final PerfettoTrackRegistry perfettoTrackRegistry;
    private final PerfettoCallstackInterner interning;

    public ConverterSession(int pid, PerfettoIdProvider idProvider, PerfettoTraceEmitter emitter) throws IOException {
        this.pid = pid;
        this.emitter = emitter;
        this.perfettoTrackRegistry = new PerfettoTrackRegistry(pid, idProvider, emitter);
        this.interning = new PerfettoCallstackInterner(idProvider);
    }

    public int pid() {
        return pid;
    }

    public PerfettoTraceEmitter emitter() {
        return emitter;
    }

    public PerfettoTrackRegistry trackRegistry() {
        return perfettoTrackRegistry;
    }

    public PerfettoCallstackInterner interning() {
        return interning;
    }

    public long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    public PerfettoTrackEventBuilder trackEvent(RecordedEvent event) {
        long startNs = toEpochNanos(event.getStartTime());
        long endNs = Math.max(startNs, toEpochNanos(event.getEndTime()));
        return new PerfettoTrackEventBuilder(this, startNs, endNs);
    }

    public PerfettoTrackEventBuilder trackEvent(long startNs, long endNs) {
        return new PerfettoTrackEventBuilder(this, startNs, endNs);
    }
}
