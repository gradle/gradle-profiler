package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.consumer.RecordedEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticGarbageCollectionEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording
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
        SyntheticRecording.record(outputFile, ["jdk.GarbageCollection"]) {
            def event = new SyntheticGarbageCollectionEvent(
                name: "Synthetic young collection",
                cause: "Unit test"
            )
            event.begin()
            event.commit()
        }
    }
}
