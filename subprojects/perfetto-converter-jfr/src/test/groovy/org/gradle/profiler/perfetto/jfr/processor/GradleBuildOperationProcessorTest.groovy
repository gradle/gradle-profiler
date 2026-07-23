package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.consumer.RecordedEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticBuildOperationEvent
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording
import perfetto.protos.Trace
import perfetto.protos.TrackEvent

class GradleBuildOperationProcessorTest extends AbstractProcessorTest {
    def "emits a build operation slice with annotations from a real JFR event"() {
        given:
        File jfrFile = temporaryFile("build-operation.jfr")
        writeSyntheticBuildOperationRecording(jfrFile, [displayName: "Resolve dependencies"])
        RecordedEvent buildOperationEvent = readSingleEvent(jfrFile, "org.gradle.internal.operations.BuildOperation")

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
            failureType   : "java.lang.RuntimeException",
            failureMessage: "boom"
        ])
        RecordedEvent buildOperationEvent = readSingleEvent(jfrFile, "org.gradle.internal.operations.BuildOperation")

        when:
        Trace trace = processEvent(new GradleBuildOperationProcessor(), buildOperationEvent, "failed-build-operation.perfetto")
        def begin = trace.packetList
            .findAll { it.hasTrackEvent() }
            .collect { it.trackEvent }
            .find { it.type == TrackEvent.Type.TYPE_SLICE_BEGIN }

        then:
        begin != null
        begin.debugAnnotationsList.any { it.name == "failureType" && it.stringValue == "java.lang.RuntimeException" }
        begin.debugAnnotationsList.any { it.name == "failureMessage" && it.stringValue == "boom" }
    }

    def "falls back to the operation id when a build operation is missing a display name"() {
        given:
        File jfrFile = temporaryFile("build-operation-without-name.jfr")
        writeSyntheticBuildOperationRecording(jfrFile)
        RecordedEvent buildOperationEvent = readSingleEvent(jfrFile, "org.gradle.internal.operations.BuildOperation")

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

    def "emits a Virtual track named after each assigned lane"() {
        given:
        def processor = new GradleBuildOperationProcessor()
        [
            buildOperation(1, 0, 0, 100),
            buildOperation(2, 0, 10, 40),  // overlaps 1 -> assigned to a second lane
        ].each { processor.buffer(it) }

        when:
        Trace trace = processEvents(processor, [], "virtual-tracks.perfetto")
        def virtualTrackNames = trace.packetList.findAll {
            it.hasTrackDescriptor() && it.trackDescriptor.name.startsWith("Virtual ")
        }.collect { it.trackDescriptor.name }

        then:
        virtualTrackNames == ["Virtual 01", "Virtual 02"]
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
        lanesByOperation(operations) == [
            1L: 1,
            2L: 1,
            3L: 2,
            4L: 1,
            5L: 2
        ]
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
        SyntheticRecording.record(outputFile, ["org.gradle.internal.operations.BuildOperation"]) {
            def event = new SyntheticBuildOperationEvent(
                displayName: null,
                operationId: 1002L,
                parentId: 1001L,
                failureType: null,
                failureMessage: null
            )
            overrides.each { key, value -> event."$key" = value }
            event.begin()
            event.commit()
        }
    }
}
