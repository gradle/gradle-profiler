package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.consumer.RecordedEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticThreadSleepEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording
import perfetto.protos.Trace
import perfetto.protos.TrackEvent

class JfrThreadStateProcessorTest extends AbstractProcessorTest {
    def "emits a named thread-state slice from a real JFR thread sleep event"() {
        given:
        File jfrFile = temporaryFile("thread-state.jfr")
        writeSyntheticThreadSleepRecording(jfrFile)
        RecordedEvent sleepEvent = readSingleEvent(jfrFile, "jdk.ThreadSleep")

        when:
        Trace trace = processEvent(new JfrThreadStateProcessor(), sleepEvent, "thread-state.perfetto")
        def trackEvents = trace.packetList.findAll { it.hasTrackEvent() }.collect { it.trackEvent }

        then:
        def begin = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }
        begin != null
        begin.name == "Sleeping"

        and:
        def end = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_END }
        end != null
        end.trackUuid == begin.trackUuid
    }

    private static void writeSyntheticThreadSleepRecording(File outputFile) {
        SyntheticRecording.record(outputFile, ["jdk.ThreadSleep"]) {
            def event = new SyntheticThreadSleepEvent()
            event.begin()
            event.commit()
        }
    }
}
