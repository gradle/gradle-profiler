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

class GradleBuildOperationProcessorTest extends AbstractProcessorTest {
    def "emits a build operation slice with annotations from a real JFR event"() {
        given:
        File jfrFile = temporaryFile("build-operation.jfr")
        writeSyntheticBuildOperationRecording(jfrFile, [displayName: "Resolve dependencies"])
        RecordedEvent buildOperationEvent = readSingleEvent(jfrFile, "org.gradle.BuildOperation")

        when:
        Trace trace = processEvent(new GradleBuildOperationProcessor(), buildOperationEvent, "build-operation.perfetto")
        def trackEvents = trace.packetList.findAll { it.hasTrackEvent() }.collect { it.trackEvent }

        then:
        trackEvents.size() == 2

        and:
        def begin = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }
        begin != null
        begin.name == "Resolve dependencies"
        begin.flowIdsCount == 0
        begin.debugAnnotationsList.any { it.name == "operationId" && it.intValue == 1002L }
        begin.debugAnnotationsList.any { it.name == "parentId" && it.intValue == 1001L }
        !begin.debugAnnotationsList.any { it.name == "failed" }
        !begin.debugAnnotationsList.any { it.name == "failureType" }
        !begin.debugAnnotationsList.any { it.name == "failureMessage" }

        and:
        def end = trackEvents.find { it.type == TrackEvent.Type.TYPE_SLICE_END }
        end != null
        end.trackUuid == begin.trackUuid
    }

    def "emits failure annotations only for failed build operations"() {
        given:
        File jfrFile = temporaryFile("failed-build-operation.jfr")
        writeSyntheticBuildOperationRecording(jfrFile, [
            displayName   : "Resolve dependencies",
            failed        : true,
            failureType   : "java.lang.RuntimeException",
            failureMessage: "boom"
        ])
        RecordedEvent buildOperationEvent = readSingleEvent(jfrFile, "org.gradle.BuildOperation")

        when:
        Trace trace = processEvent(new GradleBuildOperationProcessor(), buildOperationEvent, "failed-build-operation.perfetto")
        def begin = trace.packetList
            .findAll { it.hasTrackEvent() }
            .collect { it.trackEvent }
            .find { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }

        then:
        begin != null
        begin.debugAnnotationsList.any { it.name == "failed" && it.boolValue }
        begin.debugAnnotationsList.any { it.name == "failureType" && it.stringValue == "java.lang.RuntimeException" }
        begin.debugAnnotationsList.any { it.name == "failureMessage" && it.stringValue == "boom" }
    }

    def "falls back to the operation id when a build operation is missing a display name"() {
        given:
        File jfrFile = temporaryFile("build-operation-without-name.jfr")
        writeSyntheticBuildOperationRecording(jfrFile)
        RecordedEvent buildOperationEvent = readSingleEvent(jfrFile, "org.gradle.BuildOperation")

        when:
        Trace trace = processEvent(new GradleBuildOperationProcessor(), buildOperationEvent, "build-operation-without-name.perfetto")
        def begin = trace.packetList
            .findAll { it.hasTrackEvent() }
            .collect { it.trackEvent }
            .find { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }

        then:
        begin != null
        begin.name == "Build Operation 1002"
    }

    def "places overlapping sibling operations on separate virtual tracks and reuses them after completion"() {
        given:
        File jfrFile = temporaryFile("parallel-build-operations.jfr")
        writeParallelBuildOperationRecording(jfrFile)
        List<RecordedEvent> buildOperationEvents = readEvents(jfrFile, "org.gradle.BuildOperation").reverse()

        when:
        Trace trace = processEvents(new GradleBuildOperationProcessor(), buildOperationEvents, "parallel-build-operations.perfetto")
        def packets = trace.packetList
        def trackEventPackets = packets.findAll { it.hasTrackEvent() }
        def beginEvents = trackEventPackets.collect { it.trackEvent }
            .findAll { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }
        def beginByName = beginEvents.collectEntries { [(it.name): it] }
        def trackByName = beginByName.collectEntries { name, event -> [(name): event.trackUuid] }
        def virtualTrackNames = packets.findAll {
            it.hasTrackDescriptor() && it.trackDescriptor.name.startsWith("Virtual ")
        }.collect { it.trackDescriptor.name }

        then:
        trackByName["Parent"] == trackByName["First child"]
        trackByName["Second child"] != trackByName["First child"]
        trackByName["Third child"] == trackByName["First child"]
        virtualTrackNames.toSet() == ["Virtual 01", "Virtual 02"] as Set

        and:
        beginByName.values().every { it.flowIdsCount == 0 }
    }

    def "nests a child on its parent's lane and spills a concurrent sibling onto a new lane"() {
        given:
        def operations = [
            buildOperation(1, 0, 0, 100),  // root
            buildOperation(2, 1, 10, 30),  // child of 1
            buildOperation(3, 1, 20, 90),  // child of 1, overlaps 2 -> spills to a new lane
            buildOperation(4, 1, 40, 60),  // child of 1, starts after 2 ends -> reuses the parent lane
            buildOperation(5, 3, 50, 70),  // child of 3 -> nests on 3's lane
        ]

        expect:
        lanesByOperation(operations) == [1L: 1, 2L: 1, 3L: 2, 4L: 1, 5L: 2]
    }

    def "reuses a lane once the operation occupying it has ended"() {
        given:
        def operations = [
            buildOperation(1, 0, 0, 10),
            buildOperation(2, 0, 20, 30),  // starts after 1 ends -> reclaims the freed lane
        ]

        expect:
        lanesByOperation(operations) == [1L: 1, 2L: 1]
    }

    def "spreads operations that overlap in time across separate lanes"() {
        given:
        def operations = [
            buildOperation(1, 0, 0, 30),
            buildOperation(2, 0, 10, 40),  // overlaps 1
            buildOperation(3, 0, 20, 50),  // overlaps 1 and 2
        ]

        expect:
        lanesByOperation(operations) == [1L: 1, 2L: 2, 3L: 3]
    }

    def "assigns lanes independently of the order events were ingested"() {
        given:
        def ascending = [
            buildOperation(1, 0, 0, 100),
            buildOperation(2, 1, 10, 30),
            buildOperation(3, 1, 20, 90),
        ]

        expect:
        lanesByOperation(ascending.reverse()) == lanesByOperation(ascending)
    }

    private static Map<Long, Integer> lanesByOperation(List<GradleBuildOperationProcessor.BuildOperationRecord> operations) {
        GradleBuildOperationProcessor.assignLanes(operations)
            .collectEntries { [(it.record().operationId()): it.laneId()] }
    }

    private static GradleBuildOperationProcessor.BuildOperationRecord buildOperation(
        long operationId, long parentId, long startNs, long endNs
    ) {
        new GradleBuildOperationProcessor.BuildOperationRecord(
            startNs, endNs, (int) operationId, operationId, parentId, "op-" + operationId, null
        )
    }

    private static void writeSyntheticBuildOperationRecording(File outputFile, Map<String, ?> overrides = [:]) {
        registerSyntheticEventType()

        Recording recording = new Recording()
        try {
            recording.enable("org.gradle.BuildOperation").withoutThreshold()
            recording.start()

            def event = new SyntheticBuildOperationEvent(
                displayName: null,
                operationId: 1002L,
                parentId: 1001L,
                failed: false,
                failureType: "none",
                failureMessage: "n/a"
            )
            overrides.each { key, value -> event."$key" = value }
            event.begin()
            sleepFor(Duration.ofMillis(5))
            event.commit()

            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static void writeParallelBuildOperationRecording(File outputFile) {
        registerSyntheticEventType()

        Recording recording = new Recording()
        try {
            recording.enable("org.gradle.BuildOperation").withoutThreshold()
            recording.start()

            def parent = syntheticBuildOperation("Parent", 1001L, 0L)
            def firstChild = syntheticBuildOperation("First child", 1002L, 1001L)
            def secondChild = syntheticBuildOperation("Second child", 1003L, 1001L)
            def thirdChild = syntheticBuildOperation("Third child", 1004L, 1001L)

            parent.begin()
            sleepFor(Duration.ofMillis(5))

            firstChild.begin()
            sleepFor(Duration.ofMillis(5))

            secondChild.begin()
            sleepFor(Duration.ofMillis(5))
            secondChild.commit()

            sleepFor(Duration.ofMillis(5))
            firstChild.commit()

            sleepFor(Duration.ofMillis(5))
            thirdChild.begin()
            sleepFor(Duration.ofMillis(5))
            thirdChild.commit()

            sleepFor(Duration.ofMillis(5))
            parent.commit()

            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static SyntheticBuildOperationEvent syntheticBuildOperation(String displayName, long operationId, long parentId) {
        new SyntheticBuildOperationEvent(
            displayName: displayName,
            operationId: operationId,
            parentId: parentId,
            failed: false,
            failureType: "none",
            failureMessage: "n/a"
        )
    }

    private static void registerSyntheticEventType() {
        jdk.jfr.EventType.getEventType(SyntheticBuildOperationEvent)
    }

    @Name("org.gradle.BuildOperation")
    @Label("Synthetic Build Operation")
    @Category(["Gradle"])
    static class SyntheticBuildOperationEvent extends Event {
        String displayName
        long operationId
        long parentId
        boolean failed
        String failureType
        String failureMessage
    }
}
