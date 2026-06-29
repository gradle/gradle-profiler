package org.gradle.profiler.perfetto.jfr.processor

import java.time.Duration
import jdk.jfr.Category
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.Recording
import perfetto.protos.Trace
import perfetto.protos.TrackEvent

class JfrGcProcessorTest extends AbstractProcessorTest {
    def "emits a named GC slice with cause annotation from a real JFR event"() {
        given:
        File jfrFile = temporaryFile("gc.jfr")
        writeSyntheticGcRecording(jfrFile)
        RecordedEvent gcEvent = readSingleEvent(jfrFile, "jdk.GarbageCollection")

        when:
        Trace trace = processEvent(new JfrGcProcessor(), gcEvent, "gc.perfetto")
        def trackEvents = trace.packetList.findAll { it.hasTrackEvent() }.collect { it.trackEvent }

        then:
        trackEvents.size() == 2

        and:
        def begin = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }
        begin != null
        begin.name == "GC Synthetic young collection"
        begin.debugAnnotationsList.any {
            it.name == "cause" && it.stringValue == "Unit test"
        }

        and:
        def end = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_END }
        end != null
        end.trackUuid == begin.trackUuid
    }

    private static void writeSyntheticGcRecording(File outputFile) {
        registerSyntheticEventType()

        Recording recording = new Recording()
        try {
            recording.enable("jdk.GarbageCollection").withoutThreshold()
            recording.start()

            def event = new SyntheticGarbageCollectionEvent(
                name: "Synthetic young collection",
                cause: "Unit test"
            )
            event.begin()
            sleepFor(Duration.ofMillis(5))
            event.commit()

            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static void registerSyntheticEventType() {
        jdk.jfr.EventType.getEventType(SyntheticGarbageCollectionEvent)
    }

    @Name("jdk.GarbageCollection")
    @Label("Synthetic Garbage Collection")
    @Category(["JVM", "GC"])
    static class SyntheticGarbageCollectionEvent extends Event {
        String name
        String cause
    }
}
