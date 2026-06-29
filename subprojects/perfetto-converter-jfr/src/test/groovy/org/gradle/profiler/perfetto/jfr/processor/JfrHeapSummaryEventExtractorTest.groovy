package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordedObject
import jdk.jfr.consumer.RecordingFile
import jdk.jfr.Recording
import spock.lang.Specification
import spock.lang.TempDir

class JfrHeapSummaryEventExtractorTest extends Specification {
    @TempDir
    File temporaryDirectory

    def "default extractor reads nested heap-space committed size from a real After GC event"() {
        given:
        File jfrFile = new File(temporaryDirectory, "heap-summary-real.jfr")
        writeHeapSummaryRecording(jfrFile)
        RecordedEvent heapSummaryEvent = readAfterGcHeapSummary(jfrFile)
        RecordedObject heapSpace = heapSummaryEvent.getValue("heapSpace") as RecordedObject

        when:
        def data = new JfrHeapSummaryProcessor.HeapSummaryEventExtractor() {}.extract(heapSummaryEvent)

        then:
        data.when() == "After GC"
        data.heapUsedBytes() == heapSummaryEvent.getLong("heapUsed")
        data.heapCommittedBytes() == heapSpace.getLong("committedSize")
    }

    private static void writeHeapSummaryRecording(File outputFile) {
        Recording recording = new Recording()
        try {
            recording.enable("jdk.GCHeapSummary").withoutThreshold()
            recording.enable("jdk.GarbageCollection").withoutThreshold()
            recording.start()

            def pressure = new byte[8][]
            for (int i = 0; i < pressure.length; i++) {
                pressure[i] = new byte[2 * 1024 * 1024]
            }
            System.gc()

            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static RecordedEvent readAfterGcHeapSummary(File recordingFile) {
        RecordingFile recording = new RecordingFile(recordingFile.toPath())
        try {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent()
                if (event.eventType.name == "jdk.GCHeapSummary" && event.getString("when") == "After GC") {
                    return event
                }
            }
        } finally {
            recording.close()
        }
        throw new AssertionError("Missing After GC jdk.GCHeapSummary event")
    }
}
