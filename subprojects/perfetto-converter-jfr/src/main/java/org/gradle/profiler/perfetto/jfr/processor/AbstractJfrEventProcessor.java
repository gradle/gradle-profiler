package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.jspecify.annotations.Nullable;

abstract class AbstractJfrEventProcessor implements JfrEventProcessor<Void> {
    private final String eventName;

    protected AbstractJfrEventProcessor(String eventName) {
        this.eventName = eventName;
    }

    @Override
    public final void process(RecordedEvent event, @Nullable ConverterSession context) throws IOException {
        if (!eventName.equals(event.getEventType().getName())) {
            return;
        }
        if (context == null) {
            throw new IllegalArgumentException("ConverterSession is required to process " + eventName);
        }
        processMatchingEvent(event, context);
    }

    protected abstract void processMatchingEvent(RecordedEvent event, ConverterSession context) throws IOException;
}
