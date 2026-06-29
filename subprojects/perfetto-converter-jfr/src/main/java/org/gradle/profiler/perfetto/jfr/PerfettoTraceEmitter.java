package org.gradle.profiler.perfetto.jfr;

import java.io.IOException;
import perfetto.protos.BuiltinClock;
import perfetto.protos.ClockSnapshot;
import perfetto.protos.InternedData;
import perfetto.protos.PerfSample;
import perfetto.protos.TracePacket;
import perfetto.protos.TrackDescriptor;
import perfetto.protos.TrackEvent;

/**
 * Wraps low-level Perfetto packet construction behind event-oriented helper methods.
 *
 * <p>This keeps sequence and clock metadata consistent across slices, counters, samples, and interning deltas.
 */
public final class PerfettoTraceEmitter {
    private final PerfettoTraceWriter writer;

    public PerfettoTraceEmitter(PerfettoTraceWriter writer) {
        this.writer = writer;
    }

    public void emitTrackDescriptor(TrackDescriptor descriptor) throws IOException {
        writer.write(TracePacket.newBuilder()
            .setTrustedPacketSequenceId(PerfettoTraceWriter.PACKET_SEQUENCE_ID)
            .setTrackDescriptor(descriptor)
            .build());
    }

    public void emitInternedData(InternedData internedData) throws IOException {
        writer.write(TracePacket.newBuilder()
            .setTrustedPacketSequenceId(PerfettoTraceWriter.PACKET_SEQUENCE_ID)
            .setInternedData(internedData)
            .build());
    }

    public void emitPrimaryRealtimeClockSnapshot(long timestampNs) throws IOException {
        writer.write(TracePacket.newBuilder()
            .setTrustedPacketSequenceId(PerfettoTraceWriter.PACKET_SEQUENCE_ID)
            .setClockSnapshot(ClockSnapshot.newBuilder()
                .addClocks(ClockSnapshot.Clock.newBuilder()
                    .setClockId(BuiltinClock.BUILTIN_CLOCK_REALTIME_VALUE)
                    .setTimestamp(timestampNs))
                .setPrimaryTraceClock(BuiltinClock.BUILTIN_CLOCK_REALTIME))
            .build());
    }

    public void emitPerfSample(long timestampNs, PerfSample perfSample) throws IOException {
        writer.write(timestampedPacket(timestampNs)
            .setPerfSample(perfSample)
            .build());
    }

    public void emitSlice(long startNs, long endNs, TrackEvent.Builder baseEvent) throws IOException {
        TrackEvent base = baseEvent.build();
        if (endNs <= startNs) {
            emitTrackEvent(startNs, TrackEvent.newBuilder(base)
                .setType(TrackEvent.Type.TYPE_INSTANT)
                .build());
            return;
        }

        emitTrackEvent(startNs, TrackEvent.newBuilder(base)
            .setType(TrackEvent.Type.TYPE_SLICE_BEGIN)
            .build());
        emitTrackEvent(endNs, TrackEvent.newBuilder()
            .setTrackUuid(base.getTrackUuid())
            .setType(TrackEvent.Type.TYPE_SLICE_END)
            .build());
    }

    public void emitCounter(long timestampNs, long trackUuid, double value) throws IOException {
        emitTrackEvent(timestampNs, TrackEvent.newBuilder()
            .setTrackUuid(trackUuid)
            .setType(TrackEvent.Type.TYPE_COUNTER)
            .setDoubleCounterValue(value)
            .build());
    }

    public void emitTrackEvent(long timestampNs, TrackEvent event) throws IOException {
        writer.write(timestampedPacket(timestampNs)
            .setTrackEvent(event)
            .build());
    }

    private static TracePacket.Builder timestampedPacket(long timestampNs) {
        return TracePacket.newBuilder()
            .setTrustedPacketSequenceId(PerfettoTraceWriter.PACKET_SEQUENCE_ID)
            .setTimestamp(timestampNs)
            .setTimestampClockId(BuiltinClock.BUILTIN_CLOCK_REALTIME_VALUE);
    }
}
