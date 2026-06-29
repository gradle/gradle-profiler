package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.Category
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.Recording
import perfetto.protos.Trace
import perfetto.protos.TrackEvent

class JfrHeapSummaryProcessorTest extends AbstractProcessorTest {
    def "emits heap-used and heap-committed counters from a synthetic After GC heap summary event"() {
        given:
        File jfrFile = temporaryFile("heap-summary.jfr")
        writeSyntheticHeapSummaryRecording(jfrFile)
        RecordedEvent heapSummaryEvent = readSingleEvent(jfrFile, "jdk.GCHeapSummary")

        when:
        Trace trace = processEvent(
            new JfrHeapSummaryProcessor(new JfrHeapSummaryProcessor.HeapSummaryEventExtractor() {
                @Override
                JfrHeapSummaryProcessor.HeapSummaryEventData extract(RecordedEvent current) {
                    return new JfrHeapSummaryProcessor.HeapSummaryEventData(
                        current.getString("when"),
                        current.getLong("heapUsed"),
                        current.getLong("heapCommitted")
                    )
                }
            }),
            heapSummaryEvent,
            "heap-summary.perfetto"
        )
        def counterEvents = trace.packetList.findAll { it.hasTrackEvent() }
            .collect { it.trackEvent }
            .findAll { it.type == TrackEvent.Type.TYPE_COUNTER }

        then:
        counterEvents.size() == 2
        counterEvents*.doubleCounterValue.toSet() == [0.5d, 1.0d] as Set
        counterEvents*.trackUuid.toSet().size() == 2
    }

    private static void writeSyntheticHeapSummaryRecording(File outputFile) {
        registerSyntheticEventType()

        Recording recording = new Recording()
        try {
            recording.enable("jdk.GCHeapSummary").withoutThreshold()
            recording.start()

            new SyntheticHeapSummaryEvent(
                when: "After GC",
                heapUsed: 512L * 1024L * 1024L,
                heapCommitted: 1024L * 1024L * 1024L
            ).commit()

            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static void registerSyntheticEventType() {
        jdk.jfr.EventType.getEventType(SyntheticHeapSummaryEvent)
    }

    @Name("jdk.GCHeapSummary")
    @Label("Synthetic GC Heap Summary")
    @Category(["JVM", "GC"])
    static class SyntheticHeapSummaryEvent extends Event {
        String when
        long heapUsed
        long heapCommitted
    }
}
