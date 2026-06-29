package org.gradle.profiler.perfetto.jfr
import perfetto.protos.BuiltinClock
import perfetto.protos.Trace
import perfetto.protos.TracePacket
import perfetto.protos.TrackDescriptor
import perfetto.protos.TrackEvent
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.TempDir

class JfrToPerfettoConverterTest extends Specification {
    private static final String FIXTURE_NAME = "recording-cold-cc-hit.jfr"

    @TempDir
    File temporaryDirectory

    @Ignore("Fixture recording-cold-cc-hit.jfr was recorded by a newer JDK; jdk.jfr cannot parse it on JDK 17. Regenerate the fixture (or synthesize one at runtime) to re-enable.")
    def "converts a real JFR recording into representative Perfetto packets"() {
        given:
        File jfrFile = copyFixtureToTemporaryDirectory()
        File perfettoFile = new File(temporaryDirectory, "recording-cold-cc-hit.perfetto")

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
        sliceBeginNames(packets).contains("Check configuration cache fingerprint")
        sliceBeginNames(packets).contains("Load configuration cache state")
        sliceBeginNames(packets).contains("Run main tasks")
        sliceBeginNames(packets).contains("Alive")
        sliceBeginNames(packets).any { it.startsWith("GC ") }

        and:
        packets.any { it.hasTrackEvent() && it.trackEvent.type == TrackEvent.Type.TYPE_COUNTER }
        packets.any { it.hasPerfSample() }
        packets.any { it.hasInternedData() }
        packets.any {
            it.hasClockSnapshot() &&
                it.clockSnapshot.primaryTraceClock == BuiltinClock.BUILTIN_CLOCK_REALTIME &&
                it.clockSnapshot.clocksList.any { clock -> clock.clockId == BuiltinClock.BUILTIN_CLOCK_REALTIME_VALUE }
        }
    }

    @Ignore("Fixture recording-cold-cc-hit.jfr was recorded by a newer JDK; jdk.jfr cannot parse it on JDK 17. Regenerate the fixture (or synthesize one at runtime) to re-enable.")
    def "retains expected build operation structure from the fixture recording"() {
        given:
        File jfrFile = copyFixtureToTemporaryDirectory()
        File perfettoFile = new File(temporaryDirectory, "recording-cold-cc-hit.perfetto")

        when:
        JfrToPerfettoConverter.convert(jfrFile, perfettoFile)
        Trace trace = Trace.parseFrom(perfettoFile.bytes)
        def trackEvents = trace.packetList.findAll { it.hasTrackEvent() }.collect { it.trackEvent }

        then:
        trackEvents.count {
            it.type == TrackEvent.Type.TYPE_SLICE_BEGIN && it.name == "Check configuration cache fingerprint"
        } == 1
        trackEvents.count {
            it.type == TrackEvent.Type.TYPE_SLICE_BEGIN && it.name == "Task :help"
        } == 1
        trackEvents.count {
            it.type == TrackEvent.Type.TYPE_SLICE_BEGIN && it.name == "Execute displayHelp for :help"
        } == 1
        trackEvents.count { it.type == TrackEvent.Type.TYPE_SLICE_END } >= 21
    }

    private File copyFixtureToTemporaryDirectory() {
        File target = new File(temporaryDirectory, FIXTURE_NAME)
        target.bytes = fixtureBytes()
        return target
    }

    private byte[] fixtureBytes() {
        def resource = getClass().getResourceAsStream(FIXTURE_NAME)
        assert resource != null: "Missing test fixture ${FIXTURE_NAME}"
        try {
            return resource.readAllBytes()
        } finally {
            resource.close()
        }
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
