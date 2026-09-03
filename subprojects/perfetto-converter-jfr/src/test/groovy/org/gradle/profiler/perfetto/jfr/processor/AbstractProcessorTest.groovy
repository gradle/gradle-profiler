package org.gradle.profiler.perfetto.jfr.processor

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import org.gradle.profiler.perfetto.jfr.PerfettoIdProvider
import org.gradle.profiler.perfetto.jfr.ConverterSession
import org.gradle.profiler.perfetto.jfr.PerfettoTraceWriter
import org.gradle.profiler.perfetto.jfr.PerfettoTraceEmitter
import perfetto.protos.Trace
import spock.lang.Specification
import spock.lang.TempDir

abstract class AbstractProcessorTest extends Specification {
    protected static final int PID = 4242

    @TempDir
    File temporaryDirectory

    protected File temporaryFile(String fileName) {
        new File(temporaryDirectory, fileName)
    }

    protected Trace processEvent(JfrEventProcessor<?> processor, RecordedEvent event, String traceFileName) {
        processEvents(processor, [event], traceFileName)
    }

    protected Trace processEvents(JfrEventProcessor<?> processor, Iterable<RecordedEvent> events, String traceFileName) {
        File traceFile = temporaryFile(traceFileName)
        def writer = new PerfettoTraceWriter(traceFile.toPath())
        try {
            def emitter = new PerfettoTraceEmitter(writer)
            def context = new ConverterSession(PID, new PerfettoIdProvider(), emitter)
            processor.start(context)
            events.each { processor.process(it, context) }
            processor.finish(context)
        } finally {
            writer.close()
        }
        Trace.parseFrom(traceFile.bytes)
    }

    protected static RecordedEvent readSingleEvent(File recordingFile, String eventName, Closure<Boolean> predicate = { true }) {
        RecordingFile recording = new RecordingFile(recordingFile.toPath())
        try {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent()
                if (event.eventType.name == eventName && predicate(event)) {
                    return event
                }
            }
        } finally {
            recording.close()
        }
        throw new AssertionError("Missing JFR event ${eventName}")
    }

    protected static List<RecordedEvent> readEvents(File recordingFile, String... eventNames) {
        def names = eventNames as Set
        def events = []
        RecordingFile recording = new RecordingFile(recordingFile.toPath())
        try {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent()
                if (names.contains(event.eventType.name)) {
                    events << event
                }
            }
        } finally {
            recording.close()
        }
        assert events*.eventType*.name as Set == names
        events
    }
}
