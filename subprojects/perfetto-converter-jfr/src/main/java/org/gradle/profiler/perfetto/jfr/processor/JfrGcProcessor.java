package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.gradle.profiler.perfetto.jfr.JfrRecordFields;

/**
 * Emits garbage collection slices annotated with the reported GC cause.
 *
 * <p>Consumes:
 * <ul>
 *   <li>{@code jdk.GarbageCollection}</li>
 * </ul>
 */
public final class JfrGcProcessor extends AbstractJfrEventProcessor {
    public JfrGcProcessor() {
        super("jdk.GarbageCollection");
    }

    @Override
    protected void processMatchingEvent(RecordedEvent event, ConverterSession context) throws IOException {
        JfrRecordFields fields = JfrRecordFields.of(event);
        context.trackEvent(event)
            .onTrack(context.trackRegistry().gcTrackId())
            .name("GC " + fields.stringOr("name", "Unknown"))
            .annotateIfPresent("cause", fields.nullableString("cause"))
            .emitSlice();
    }
}
