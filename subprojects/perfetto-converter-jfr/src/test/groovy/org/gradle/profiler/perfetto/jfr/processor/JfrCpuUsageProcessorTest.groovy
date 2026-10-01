package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.consumer.RecordedEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticCpuLoadEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording
import perfetto.protos.Trace
import perfetto.protos.TrackEvent

class JfrCpuUsageProcessorTest extends AbstractProcessorTest {
    def "emits JVM and system CPU counters from a real JFR event"() {
        given:
        File jfrFile = temporaryFile("cpu.jfr")
        writeSyntheticCpuRecording(jfrFile)
        RecordedEvent cpuEvent = readSingleEvent(jfrFile, "jdk.CPULoad")

        when:
        Trace trace = processEvent(new JfrCpuUsageProcessor(), cpuEvent, "cpu.perfetto")
        def counterEvents = trace.packetList.findAll { it.hasTrackEvent() }
            .collect { it.trackEvent }
            .findAll { it.type == TrackEvent.Type.TYPE_COUNTER }

        then:
        counterEvents.size() == 2
        counterEvents*.doubleCounterValue.toSet() == [
            cpuEvent.getDouble("jvmUser") * 100d + cpuEvent.getDouble("jvmSystem") * 100d,
            cpuEvent.getDouble("machineTotal") * 100d
        ] as Set
        counterEvents*.trackUuid.toSet().size() == 2
    }

    private static void writeSyntheticCpuRecording(File outputFile) {
        SyntheticRecording.record(outputFile, ["jdk.CPULoad"]) {
            new SyntheticCpuLoadEvent(
                jvmUser: 0.21d,
                jvmSystem: 0.09d,
                machineTotal: 0.57d
            ).commit()
        }
    }
}
