package org.gradle.trace;

import org.gradle.api.invocation.Gradle;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceResult {
    private final Map<String, DurationEvent> events = new LinkedHashMap<>();
    private final AsynchronousTraceWriter traceWriter = new AsynchronousTraceWriter();

    public void count(String name, String metric, Map<String, Double> info) {
        count(name, metric, info, null);
    }

    public void count(String name, String metric, Map<String, Double> info, String colorName) {
        traceWriter.add(new CountEvent(metric, info, colorName));
    }

    public void start(String name, String category, long timestampNanos) {
        start(name, category, timestampNanos, null);
    }

    public void start(String name, String category, long timestampNanos, String colorName) {
        events.put(name, new DurationEvent(name, category, timestampNanos, new HashMap<>(), colorName));
    }

    public void finish(String name, long timestampNanos, Map<String, String> info) {
        DurationEvent event = events.remove(name);
        if (event != null) {
            event.finished(timestampNanos);
            event.getInfo().putAll(info);
            traceWriter.add(event);
        }
    }

    public void finalizeTraceFile(Gradle gradle) {
        for (DurationEvent unfinishedEvent : events.values()) {
            traceWriter.add(unfinishedEvent);
        }

        traceWriter.finish(gradle);
    }

}
