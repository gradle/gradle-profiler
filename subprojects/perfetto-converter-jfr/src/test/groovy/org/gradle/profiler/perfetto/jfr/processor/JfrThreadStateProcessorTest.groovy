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
        registerSyntheticEventType()

        Recording recording = new Recording()
        try {
            recording.enable("jdk.ThreadSleep").withoutThreshold()
            recording.start()

            def event = new SyntheticThreadSleepEvent()
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
        jdk.jfr.EventType.getEventType(SyntheticThreadSleepEvent)
    }

    @Name("jdk.ThreadSleep")
    @Label("Synthetic Thread Sleep")
    @Category(["JVM", "Threads"])
    static class SyntheticThreadSleepEvent extends Event {
    }
}
