package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordedObject
import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording

class JfrHeapSummaryEventExtractorTest extends AbstractProcessorTest {
    def "default extractor reads nested heap-space committed size from a real After GC event"() {
        given:
        File jfrFile = temporaryFile("heap-summary-real.jfr")
        writeHeapSummaryRecording(jfrFile)
        RecordedEvent heapSummaryEvent = readSingleEvent(jfrFile, "jdk.GCHeapSummary") {
            it.getString("when") == "After GC"
        }
        RecordedObject heapSpace = heapSummaryEvent.getValue("heapSpace") as RecordedObject

        when:
        def data = new JfrHeapSummaryProcessor.HeapSummaryEventExtractor() {}.extract(heapSummaryEvent)

        then:
        data.when() == "After GC"
        data.heapUsedBytes() == heapSummaryEvent.getLong("heapUsed")
        data.heapCommittedBytes() == heapSpace.getLong("committedSize")
    }

    private static void writeHeapSummaryRecording(File outputFile) {
        // The real JVM event is needed here: the default extractor navigates the nested heapSpace
        // object, which synthetic events cannot carry.
        SyntheticRecording.record(outputFile, ["jdk.GCHeapSummary"]) {
            System.gc()
        }
    }
}
