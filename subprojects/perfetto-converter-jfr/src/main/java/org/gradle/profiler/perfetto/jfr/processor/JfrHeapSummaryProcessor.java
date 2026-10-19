package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.gradle.profiler.perfetto.jfr.JfrRecordFields;

/**
 * Emits heap usage counters from post-GC heap summary events.
 *
 * <p>Consumes:
 * <ul>
 *   <li>{@code jdk.GCHeapSummary}</li>
 * </ul>
 */
public final class JfrHeapSummaryProcessor extends AbstractJfrEventProcessor {
    private static final double BYTES_PER_GIB = 1024d * 1024d * 1024d;
    private static final HeapSummaryEventExtractor DEFAULT_EXTRACTOR = new HeapSummaryEventExtractor() {
    };
    private final HeapSummaryEventExtractor extractor;

    public JfrHeapSummaryProcessor() {
        this(DEFAULT_EXTRACTOR);
    }

    public JfrHeapSummaryProcessor(HeapSummaryEventExtractor extractor) {
        super("jdk.GCHeapSummary");
        this.extractor = extractor;
    }

    @Override
    protected void processMatchingEvent(RecordedEvent event, ConverterSession context) throws IOException {
        HeapSummaryEventData data = extractor.extract(event);
        if (!"After GC".equals(data.when())) {
            return;
        }

        context.trackEvent(event)
            .onTrack(context.trackRegistry().heapUsedTrackId())
            .emitCounter(data.heapUsedBytes() / BYTES_PER_GIB);
        context.trackEvent(event)
            .onTrack(context.trackRegistry().heapCommittedTrackId())
            .emitCounter(data.heapCommittedBytes() / BYTES_PER_GIB);
    }

    /**
     * Extracts flat heap-summary data from a {@link RecordedEvent}.
     *
     * <p>The built-in {@code jdk.GCHeapSummary} event exposes {@code heapSpace} as a nested
     * {@code RecordedObject}, making testing with synthetic JFR events problematic.
     *
     * This processor isolates the logic for extracting heap summary data from a {@code RecordedEvent} into a separate interface,
     * allowing tests to provide synthetic event data without needing to construct complex nested {@code RecordedObject} instances.
     */
    public interface HeapSummaryEventExtractor {
        default HeapSummaryEventData extract(RecordedEvent event) {
            JfrRecordFields fields = JfrRecordFields.of(event);
            JfrRecordFields heapSpace = fields.object("heapSpace");
            return new HeapSummaryEventData(
                fields.nullableString("when"),
                fields.longOrZero("heapUsed"),
                heapSpace != null ? heapSpace.longOrZero("committedSize") : 0L
            );
        }
    }

    public record HeapSummaryEventData(
        String when,
        long heapUsedBytes,
        long heapCommittedBytes
    ) {
    }
}
