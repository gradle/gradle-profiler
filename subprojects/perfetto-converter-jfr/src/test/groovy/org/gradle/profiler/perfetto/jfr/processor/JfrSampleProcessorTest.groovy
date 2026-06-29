package org.gradle.profiler.perfetto.jfr.processor

import java.time.Duration
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordedEvent
import perfetto.protos.Trace

class JfrSampleProcessorTest extends AbstractProcessorTest {
    def "emits a perf sample for the recorded sampledThread from a real execution sample event"() {
        given:
        File jfrFile = temporaryFile("execution-sample.jfr")
        writeExecutionSampleRecording(jfrFile)
        RecordedEvent sampleEvent = readSingleEvent(jfrFile, "jdk.ExecutionSample")

        when:
        Trace trace = processEvent(new JfrSampleProcessor(), sampleEvent, "execution-sample.perfetto")
        def perfSamples = trace.packetList.findAll { it.hasPerfSample() }.collect { it.perfSample }
        def sampledThread = sampleEvent.getThread("sampledThread")

        then:
        sampleEvent.thread == null
        sampleEvent.hasField("sampledThread")
        sampledThread != null

        and:
        perfSamples.size() == 1
        perfSamples[0].pid == PID
        perfSamples[0].tid == (int) sampledThread.getOSThreadId()
        perfSamples[0].callstackIid > 0

        and:
        trace.packetList.count { it.hasInternedData() } == 1
    }

    private static void writeExecutionSampleRecording(File outputFile) {
        Recording recording = new Recording()
        try {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10))
            recording.start()
            burnCpu(Duration.ofMillis(400))
            recording.stop()
            recording.dump(outputFile.toPath())
        } finally {
            recording.close()
        }
    }

    private static void burnCpu(Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos()
        long noise = 0L
        while (System.nanoTime() < deadline) {
            noise += System.nanoTime() & 7L
        }
        if (noise == Long.MIN_VALUE) {
            throw new AssertionError("Impossible guard to keep CPU work observable")
        }
    }
}
