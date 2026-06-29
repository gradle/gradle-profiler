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
    public boolean process(RecordedEvent event, @Nullable ConverterSession context) throws IOException {
        if (startTimestampNs == null) {
            startTimestampNs = toEpochNanos(event.getStartTime());
        }
        if (!JVM_INFO_EVENT.equals(event.getEventType().getName()) || !event.hasField("pid")) {
            return false;
        }
        long recordedPid = event.getLong("pid");
        if (recordedPid <= 0 || recordedPid > Integer.MAX_VALUE) {
            return false;
        }
        pid = (int) recordedPid;
        return true;
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
