package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.jspecify.annotations.Nullable;

/**
 * Common interface for processing JFR events into Perfetto trace output.
 *
 * @param <R> the type of the optional result returned by the processor after finishing, if any.
 */
public interface JfrEventProcessor<R> {

    /**
     * Starts processing, before any events are seen.
     * This allows the processor to emit trace data that must precede its event output, such as sequence-scoped defaults.
     *
     * @param context the conversion session providing access to shared state and the trace emitter
     */
    default void start(@Nullable ConverterSession context) throws IOException {
    }

    /**
     * Processes a single JFR event, potentially emitting a Perfetto trace event using the provided context.
     *
     * @param event the JFR event to process
     * @param context the conversion session providing access to shared state and the trace emitter
     * @return true if the processing is complete and the processor can be discarded, or false if it should continue processing more events
     */
    boolean process(RecordedEvent event, @Nullable ConverterSession context) throws IOException;

    /**
     * Finishes processing and optionally returns a result.
     * This is called after all events have been processed, allowing the processor to emit any final trace data or perform cleanup.
     *
     * @param context the conversion session providing access to shared state and the trace emitter, if needed for finalization
     * @return an optional result of type R, which may contain summary information or other data derived from the processed events
     */
    default Optional<R> finish(@Nullable ConverterSession context) throws IOException {
        return Optional.empty();
    }
}
