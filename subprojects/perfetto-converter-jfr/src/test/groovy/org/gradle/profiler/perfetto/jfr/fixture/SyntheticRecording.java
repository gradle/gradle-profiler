package org.gradle.profiler.perfetto.jfr.fixture;

import java.io.File;
import java.io.IOException;
import java.util.List;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Recording;

/**
 * Writes fully synthesized JFR recordings for tests, replacing any dependency on prerecorded binary fixtures.
 *
 * <p>Committing the {@code Synthetic*Event} types in-process is the only supported way to obtain
 * {@code RecordedEvent} instances with the right shape: the {@code jdk.jfr.consumer} types cannot
 * be constructed directly, and {@code jdk.jfr} offers no way to write events with explicit timestamps.
 */
public final class SyntheticRecording {
    public static final String ROOT_BUILD_OPERATION = "Run tasks";
    public static final String CHILD_BUILD_OPERATION = "Resolve dependencies";
    public static final String GC_SLICE_NAME = "GC Synthetic young collection";

    private static final List<Class<? extends Event>> SYNTHETIC_EVENT_TYPES = List.of(
        SyntheticJvmInformationEvent.class,
        SyntheticBuildOperationEvent.class,
        SyntheticGarbageCollectionEvent.class,
        SyntheticCpuLoadEvent.class,
        SyntheticHeapSummaryEvent.class,
        SyntheticThreadStartEvent.class,
        SyntheticThreadEndEvent.class,
        SyntheticThreadSleepEvent.class
    );

    private static final List<String> CONVERTIBLE_EVENTS = List.of(
        "jdk.JVMInformation",
        "org.gradle.internal.operations.BuildOperation",
        "jdk.GarbageCollection",
        "jdk.CPULoad",
        "jdk.GCHeapSummary",
        "jdk.ThreadStart",
        "jdk.ThreadEnd"
    );

    private SyntheticRecording() {
    }

    /**
     * Records the events committed by {@code events} into {@code outputFile}, with the named
     * event types enabled without a duration threshold.
     */
    public static void record(File outputFile, List<String> enabledEvents, Runnable events) throws IOException {
        SYNTHETIC_EVENT_TYPES.forEach(EventType::getEventType);
        try (Recording recording = new Recording()) {
            enabledEvents.forEach(name -> recording.enable(name).withoutThreshold());
            recording.start();
            events.run();
            recording.stop();
            recording.dump(outputFile.toPath());
        }
    }

    /**
     * Writes a recording the converter can process end-to-end: one representative event per
     * processor, plus the {@code jdk.JVMInformation} event the converter needs to discover the PID.
     */
    public static void writeConvertibleRecording(File outputFile) throws IOException {
        record(outputFile, CONVERTIBLE_EVENTS, () -> {
            SyntheticJvmInformationEvent jvmInformation = new SyntheticJvmInformationEvent();
            jvmInformation.pid = 4242L;
            jvmInformation.commit();

            new SyntheticThreadStartEvent().commit();

            SyntheticBuildOperationEvent root = buildOperation(ROOT_BUILD_OPERATION, 1L, 0L);
            SyntheticBuildOperationEvent child = buildOperation(CHILD_BUILD_OPERATION, 2L, 1L);
            root.begin();
            child.begin();
            child.commit();
            root.commit();

            SyntheticGarbageCollectionEvent gc = new SyntheticGarbageCollectionEvent();
            gc.name = "Synthetic young collection";
            gc.cause = "Unit test";
            gc.begin();
            gc.commit();

            SyntheticCpuLoadEvent cpuLoad = new SyntheticCpuLoadEvent();
            cpuLoad.jvmUser = 0.2d;
            cpuLoad.jvmSystem = 0.1d;
            cpuLoad.machineTotal = 0.5d;
            cpuLoad.commit();

            SyntheticHeapSummaryEvent heapSummary = new SyntheticHeapSummaryEvent();
            heapSummary.when = "After GC";
            heapSummary.heapUsed = 512L * 1024L * 1024L;
            heapSummary.heapCommitted = 1024L * 1024L * 1024L;
            heapSummary.commit();

            new SyntheticThreadEndEvent().commit();
        });
    }

    private static SyntheticBuildOperationEvent buildOperation(String displayName, long operationId, long parentId) {
        SyntheticBuildOperationEvent event = new SyntheticBuildOperationEvent();
        event.displayName = displayName;
        event.operationId = operationId;
        event.parentId = parentId;
        return event;
    }
}
