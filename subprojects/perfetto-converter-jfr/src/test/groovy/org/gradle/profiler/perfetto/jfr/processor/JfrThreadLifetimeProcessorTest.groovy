package org.gradle.profiler.perfetto.jfr.processor

import java.time.Duration
import jdk.jfr.Category
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import org.gradle.profiler.perfetto.jfr.ConverterSession
import org.gradle.profiler.perfetto.jfr.PerfettoIdProvider
import org.gradle.profiler.perfetto.jfr.PerfettoTraceWriter
import org.gradle.profiler.perfetto.jfr.PerfettoTraceEmitter
import perfetto.protos.Trace
import perfetto.protos.TrackEvent
import spock.lang.Specification
import spock.lang.TempDir

class JfrThreadLifetimeProcessorTest extends Specification {
    private static final int PID = 4242

    @TempDir
    File temporaryDirectory

    def "derives and emits an Alive slice from matching lifecycle events"() {
        given:
        File jfrFile = temporaryFile("thread-lifecycle.jfr")
        File traceFile = temporaryFile("thread-lifetime.perfetto")
        writeSyntheticLifecycleRecording(jfrFile)
        def processor = new JfrThreadLifetimeProcessor()
        def writer = new PerfettoTraceWriter(traceFile.toPath())

        when:
        try {
            def context = new ConverterSession(PID, new PerfettoIdProvider(), new PerfettoTraceEmitter(writer))
            RecordingFile recording = new RecordingFile(jfrFile.toPath())
            try {
                while (recording.hasMoreEvents()) {
                    processor.process(recording.readEvent(), context)
                }
            } finally {
                recording.close()
            }
            processor.finish(context)
        } finally {
            writer.close()
        }
        Trace trace = Trace.parseFrom(traceFile.bytes)
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

    private File temporaryFile(String fileName) {
        new File(temporaryDirectory, fileName)
    }

    private static void writeSyntheticLifecycleRecording(File outputFile) {
        registerSyntheticEventTypes()

        Recording recording = new Recording()
        try {
            recording.enable("jdk.ThreadStart").withoutThreshold()
            recording.enable("jdk.ThreadEnd").withoutThreshold()
            recording.start()

            new SyntheticThreadStartEvent().commit()
            sleepFor(Duration.ofMillis(5))
            new SyntheticThreadEndEvent().commit()

            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static void registerSyntheticEventTypes() {
        jdk.jfr.EventType.getEventType(SyntheticThreadStartEvent)
        jdk.jfr.EventType.getEventType(SyntheticThreadEndEvent)
    }

    private static void sleepFor(Duration duration) {
        try {
            Thread.sleep(duration.toMillis())
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt()
            throw new RuntimeException(ex)
        }
    }

    @Name("jdk.ThreadStart")
    @Label("Synthetic Thread Start")
    @Category(["JVM", "Threads"])
    static class SyntheticThreadStartEvent extends Event {
    }

    @Name("jdk.ThreadEnd")
    @Label("Synthetic Thread End")
    @Category(["JVM", "Threads"])
    static class SyntheticThreadEndEvent extends Event {
    }
}
