package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.Category
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.Recording
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
            (cpuEvent.getDouble("jvmUser") + cpuEvent.getDouble("jvmSystem")) * 100d,
            cpuEvent.getDouble("machineTotal") * 100d
        ] as Set
        counterEvents*.trackUuid.toSet().size() == 2
    }

    private static void writeSyntheticCpuRecording(File outputFile) {
        registerSyntheticEventType()

        Recording recording = new Recording()
        try {
            recording.enable("jdk.CPULoad").withoutThreshold()
            recording.start()

            new SyntheticCpuLoadEvent(
                jvmUser: 0.21d,
                jvmSystem: 0.09d,
                machineTotal: 0.57d
            ).commit()

            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static void registerSyntheticEventType() {
        jdk.jfr.EventType.getEventType(SyntheticCpuLoadEvent)
    }

    @Name("jdk.CPULoad")
    @Label("Synthetic CPU Load")
    @Category(["JVM", "System"])
    static class SyntheticCpuLoadEvent extends Event {
        double jvmUser
        double jvmSystem
        double machineTotal
    }
}
