package org.gradle.profiler.perfetto.jfr

import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording
import perfetto.protos.BuiltinClock
import perfetto.protos.Trace
import perfetto.protos.TracePacket
import perfetto.protos.TrackDescriptor
import perfetto.protos.TrackEvent
import spock.lang.Specification
import spock.lang.TempDir

class JfrToPerfettoConverterTest extends Specification {
    @TempDir
    File temporaryDirectory

    def "converts a synthesized JFR recording into representative Perfetto packets"() {
        given:
        File jfrFile = new File(temporaryDirectory, "recording.jfr")
        File perfettoFile = new File(temporaryDirectory, "recording.perfetto")
        SyntheticRecording.writeConvertibleRecording(jfrFile)

        when:
        JfrToPerfettoConverter.convert(jfrFile, perfettoFile)
        Trace trace = Trace.parseFrom(perfettoFile.bytes)
        def packets = trace.packetList

        then:
        perfettoFile.isFile()

        and:
        trackNames(packets).containsAll(["Overview", "CPU", "Memory", "Build Operations"])
        overviewChildRanks(packets) == ["CPU": 0L, "Memory": 1L, "Build Operations": 2L]
        childOrderingByTrackName(packets, ["Overview", "CPU", "Memory", "Build Operations"]).every {
            it.value == TrackDescriptor.ChildTracksOrdering.EXPLICIT
        }

        and:
        sliceBeginNames(packets).contains(SyntheticRecording.ROOT_BUILD_OPERATION)
        sliceBeginNames(packets).contains(SyntheticRecording.CHILD_BUILD_OPERATION)
        sliceBeginNames(packets).contains(SyntheticRecording.GC_SLICE_NAME)
        sliceBeginNames(packets).contains("Alive")

        and:
        packets.any { it.hasTrackEvent() && it.trackEvent.type == TrackEvent.Type.TYPE_COUNTER }
        packets.any {
            it.hasClockSnapshot() &&
                it.clockSnapshot.primaryTraceClock == BuiltinClock.BUILTIN_CLOCK_REALTIME &&
                it.clockSnapshot.clocksList.any { clock -> clock.clockId == BuiltinClock.BUILTIN_CLOCK_REALTIME_VALUE }
        }
    }

    def "retains the build operation structure of the synthesized recording"() {
        given:
        File jfrFile = new File(temporaryDirectory, "recording.jfr")
        File perfettoFile = new File(temporaryDirectory, "recording.perfetto")
        SyntheticRecording.writeConvertibleRecording(jfrFile)

        when:
        JfrToPerfettoConverter.convert(jfrFile, perfettoFile)
        Trace trace = Trace.parseFrom(perfettoFile.bytes)
        def trackEvents = trace.packetList.findAll { it.hasTrackEvent() }.collect { it.trackEvent }

        then:
        trackEvents.count {
            it.type == TrackEvent.Type.TYPE_SLICE_BEGIN && it.name == SyntheticRecording.ROOT_BUILD_OPERATION
        } == 1
        trackEvents.count {
            it.type == TrackEvent.Type.TYPE_SLICE_BEGIN && it.name == SyntheticRecording.CHILD_BUILD_OPERATION
        } == 1
        trackEvents.count {
            it.type == TrackEvent.Type.TYPE_SLICE_BEGIN && it.name == SyntheticRecording.GC_SLICE_NAME
        } == 1
        // Two build operations, one GC slice and at least one Alive slice all get a matching end.
        trackEvents.count { it.type == TrackEvent.Type.TYPE_SLICE_END } >= 4
    }

    private static Set<String> trackNames(List<TracePacket> packets) {
        packets.findAll { it.hasTrackDescriptor() }
            .collect { it.trackDescriptor.name }
            .findAll { !it.isBlank() }
            .toSet()
    }

    private static List<String> sliceBeginNames(List<TracePacket> packets) {
        packets.findAll { it.hasTrackEvent() }
            .collect { it.trackEvent }
            .findAll { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }
            .collect { it.name }
    }

    private static Map<String, Long> overviewChildRanks(List<TracePacket> packets) {
        def descriptors = packets.findAll { it.hasTrackDescriptor() }
            .collect { it.trackDescriptor }
        def overview = descriptors.find { it.name == "Overview" }
        assert overview != null
        descriptors
            .findAll { it.parentUuid == overview.uuid && ["CPU", "Memory", "Build Operations"].contains(it.name) }
            .collectEntries { [(it.name): it.siblingOrderRank] }
    }

    private static Map<String, TrackDescriptor.ChildTracksOrdering> childOrderingByTrackName(List<TracePacket> packets, List<String> names) {
        packets.findAll { it.hasTrackDescriptor() }
            .collect { it.trackDescriptor }
            .findAll { names.contains(it.name) }
            .collectEntries { [(it.name): it.childOrdering] }
    }
}
