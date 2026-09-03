package org.gradle.profiler.perfetto.jfr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import perfetto.protos.CounterDescriptor;
import perfetto.protos.ProcessDescriptor;
import perfetto.protos.ThreadDescriptor;
import perfetto.protos.TrackDescriptor;

/**
 * Owns the Perfetto track hierarchy for a converted recording.
 *
 * <p>Static overview tracks are declared eagerly, while per-thread and virtual build-operation tracks are created on demand.
 */
public final class PerfettoTrackRegistry {
    private final int pid;
    private final PerfettoIdProvider idProvider;
    private final PerfettoTraceEmitter emitter;
    private final Map<Long, Long> threadTracks = new HashMap<>();
    private final Map<Integer, Long> buildOperationTracks = new HashMap<>();

    private final long processTrackId;
    private final long overviewTrackId;
    private final long cpuTrackId;
    private final long memoryTrackId;
    private final long buildOperationsTrackId;
    private final long jvmCpuCounterTrackId;
    private final long systemCpuCounterTrackId;
    private final long gcTrackId;
    private final long heapUsedTrackId;
    private final long heapCommittedTrackId;

    public PerfettoTrackRegistry(int pid, PerfettoIdProvider idProvider, PerfettoTraceEmitter emitter) throws IOException {
        this.pid = pid;
        this.idProvider = idProvider;
        this.emitter = emitter;
        processTrackId = idProvider.nextId();
        overviewTrackId = idProvider.nextId();
        cpuTrackId = idProvider.nextId();
        memoryTrackId = idProvider.nextId();
        buildOperationsTrackId = idProvider.nextId();
        jvmCpuCounterTrackId = idProvider.nextId();
        systemCpuCounterTrackId = idProvider.nextId();
        gcTrackId = idProvider.nextId();
        heapUsedTrackId = idProvider.nextId();
        heapCommittedTrackId = idProvider.nextId();
        emitStaticTracks();
    }

    public long jvmCpuCounterTrackId() {
        return jvmCpuCounterTrackId;
    }

    public long systemCpuCounterTrackId() {
        return systemCpuCounterTrackId;
    }

    public long gcTrackId() {
        return gcTrackId;
    }

    public long heapUsedTrackId() {
        return heapUsedTrackId;
    }

    public long heapCommittedTrackId() {
        return heapCommittedTrackId;
    }

    public Long ensureThreadTrack(ThreadIdentity thread) throws IOException {
        if (thread == null) {
            return null;
        }
        Long existing = threadTracks.get(thread.trackKey());
        if (existing != null) {
            return existing;
        }

        long trackId = idProvider.nextId();
        threadTracks.put(thread.trackKey(), trackId);
        emitter.emitTrackDescriptor(TrackDescriptor.newBuilder()
            .setUuid(trackId)
            .setParentUuid(processTrackId)
            .setName(thread.name())
            .setThread(ThreadDescriptor.newBuilder()
                .setPid(pid)
                .setTid(thread.tid())
                .setThreadName(thread.name()))
            .build());
        return trackId;
    }

    public long ensureBuildOperationTrack(int virtualThread) throws IOException {
        Long existing = buildOperationTracks.get(virtualThread);
        if (existing != null) {
            return existing;
        }

        long trackId = idProvider.nextId();
        buildOperationTracks.put(virtualThread, trackId);
        emitter.emitTrackDescriptor(TrackDescriptor.newBuilder()
            .setUuid(trackId)
            .setParentUuid(buildOperationsTrackId)
            .setName(String.format(Locale.ROOT, "Virtual %02d", virtualThread))
            .setSiblingOrderRank(Math.max(0, virtualThread - 1))
            .build());
        return trackId;
    }

    private void emitStaticTracks() throws IOException {
        emitter.emitTrackDescriptor(TrackDescriptor.newBuilder()
            .setUuid(processTrackId)
            .setName("Gradle JVM")
            .setProcess(ProcessDescriptor.newBuilder()
                .setPid(pid)
                .setProcessName("Gradle JVM"))
            .build());

        emitter.emitTrackDescriptor(groupTrack(overviewTrackId, 0L, "Overview", 0));
        emitter.emitTrackDescriptor(groupTrack(cpuTrackId, overviewTrackId, "CPU", 0));
        emitter.emitTrackDescriptor(groupTrack(memoryTrackId, overviewTrackId, "Memory", 1));
        emitter.emitTrackDescriptor(groupTrack(buildOperationsTrackId, overviewTrackId, "Build Operations", 2));

        emitter.emitTrackDescriptor(counterTrack(jvmCpuCounterTrackId, cpuTrackId, "JVM CPU %", 0, "percent"));
        emitter.emitTrackDescriptor(counterTrack(systemCpuCounterTrackId, cpuTrackId, "System CPU %", 1, "percent"));
        emitter.emitTrackDescriptor(sliceTrack(gcTrackId, memoryTrackId, "GC", 0));
        emitter.emitTrackDescriptor(counterTrack(heapUsedTrackId, memoryTrackId, "Heap used (after GC)", 1, "GiB"));
        emitter.emitTrackDescriptor(counterTrack(heapCommittedTrackId, memoryTrackId, "Heap committed", 2, "GiB"));
    }

    private static TrackDescriptor groupTrack(long uuid, long parentUuid, String name, int order) {
        TrackDescriptor.Builder builder = TrackDescriptor.newBuilder()
            .setUuid(uuid)
            .setName(name)
            .setChildOrdering(TrackDescriptor.ChildTracksOrdering.EXPLICIT)
            .setSiblingOrderRank(order);
        if (parentUuid > 0) {
            builder.setParentUuid(parentUuid);
        }
        return builder.build();
    }

    private static TrackDescriptor sliceTrack(long uuid, long parentUuid, String name, int order) {
        return TrackDescriptor.newBuilder()
            .setUuid(uuid)
            .setParentUuid(parentUuid)
            .setName(name)
            .setSiblingOrderRank(order)
            .build();
    }

    private static TrackDescriptor counterTrack(long uuid, long parentUuid, String name, int order, String unitName) {
        return TrackDescriptor.newBuilder()
            .setUuid(uuid)
            .setParentUuid(parentUuid)
            .setName(name)
            .setSiblingOrderRank(order)
            .setCounter(CounterDescriptor.newBuilder()
                .setUnit(CounterDescriptor.Unit.UNIT_UNSPECIFIED)
                .setUnitName(unitName))
            .build();
    }
}
