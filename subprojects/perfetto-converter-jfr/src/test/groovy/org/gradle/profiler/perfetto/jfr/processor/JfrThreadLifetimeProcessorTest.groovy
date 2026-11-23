package org.gradle.profiler.perfetto.jfr.processor

import org.gradle.profiler.perfetto.jfr.fixture.SyntheticThreadEndEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticThreadStartEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording
import perfetto.protos.Trace
import perfetto.protos.TrackEvent

class JfrThreadLifetimeProcessorTest extends AbstractProcessorTest {
    def "derives and emits an Alive slice from matching lifecycle events"() {
        given:
        File jfrFile = temporaryFile("thread-lifecycle.jfr")
        writeSyntheticLifecycleRecording(jfrFile)
        def lifecycleEvents = readEvents(jfrFile, "jdk.ThreadStart", "jdk.ThreadEnd")

        when:
        Trace trace = processEvents(new JfrThreadLifetimeProcessor(), lifecycleEvents, "thread-lifetime.perfetto")
        def trackEvents = trace.packetList.findAll { it.hasTrackEvent() }.collect { it.trackEvent }

        then:
        def begin = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }
        begin != null
        begin.name == "Alive"

        and:
        def end = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_END }
        end != null
        end.trackUuid == begin.trackUuid
    }

    private static void writeSyntheticLifecycleRecording(File outputFile) {
        SyntheticRecording.record(outputFile, ["jdk.ThreadStart", "jdk.ThreadEnd"]) {
            new SyntheticThreadStartEvent().commit()
            new SyntheticThreadEndEvent().commit()
        }
    }
}
