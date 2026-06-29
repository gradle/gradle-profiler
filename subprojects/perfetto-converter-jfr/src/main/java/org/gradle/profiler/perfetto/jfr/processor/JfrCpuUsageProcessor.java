package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.gradle.profiler.perfetto.jfr.JfrRecordFields;

/**
 * Emits JVM and machine CPU load counters from JFR CPU usage samples.
 *
 * <p>Consumes:
 * <ul>
 *   <li>{@code jdk.CPULoad}</li>
 * </ul>
 */
public final class JfrCpuUsageProcessor extends AbstractJfrEventProcessor {
    public JfrCpuUsageProcessor() {
        super("jdk.CPULoad");
    }

    @Override
    protected void processMatchingEvent(RecordedEvent event, ConverterSession context) throws IOException {
        JfrRecordFields fields = JfrRecordFields.of(event);
        context.trackEvent(event)
            .onTrack(context.trackRegistry().jvmCpuCounterTrackId())
            .emitCounter(percent(fields, "jvmUser") + percent(fields, "jvmSystem"));
        context.trackEvent(event)
            .onTrack(context.trackRegistry().systemCpuCounterTrackId())
            .emitCounter(percent(fields, "machineTotal"));
    }

    private static double percent(JfrRecordFields fields, String fieldName) {
        return fields.doubleOrZero(fieldName) * 100d;
    }
}
