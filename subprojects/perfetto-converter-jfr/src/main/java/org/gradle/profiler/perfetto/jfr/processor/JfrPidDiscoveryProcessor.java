package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.jspecify.annotations.Nullable;

public final class JfrPidDiscoveryProcessor implements JfrEventProcessor<JfrPidDiscoveryProcessor.ConversionPlan> {
    private static final String JVM_INFO_EVENT = "jdk.JVMInformation";

    private final Path input;
    private Long startTimestampNs;
    private Integer pid;

    public JfrPidDiscoveryProcessor(Path input) {
        this.input = input;
    }

    @Override
    public void process(RecordedEvent event, @Nullable ConverterSession context) throws IOException {
        if (startTimestampNs == null) {
            startTimestampNs = toEpochNanos(event.getStartTime());
        }
        if (!JVM_INFO_EVENT.equals(event.getEventType().getName()) || !event.hasField("pid")) {
            return;
        }
        long recordedPid = event.getLong("pid");
        if (recordedPid <= 0 || recordedPid > Integer.MAX_VALUE) {
            return;
        }
        pid = (int) recordedPid;
    }

    /**
     * True once the PID has been discovered and no further events need to be read.
     */
    public boolean isComplete() {
        return pid != null;
    }

    @Override
    public Optional<ConversionPlan> finish(@Nullable ConverterSession context) {
        if (startTimestampNs == null || pid == null) {
            return Optional.empty();
        }
        return Optional.of(new ConversionPlan(input, pid, startTimestampNs));
    }

    private static long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    public record ConversionPlan(
        Path input,
        int pid,
        long startTimestampNs
    ) {
    }
}
