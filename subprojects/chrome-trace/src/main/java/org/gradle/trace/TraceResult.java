package org.gradle.trace;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TraceResult {
    private final Map<String, DurationEvent> events = new ConcurrentHashMap<>();
    private final AsynchronousTraceWriter traceWriter;

    public TraceResult(File traceFile) {
        traceWriter = new AsynchronousTraceWriter(traceFile);
    }

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

    public void finalizeTraceFile() {
        for (String unfinishedEvent : new ArrayList<>(events.keySet())) {
            finish(unfinishedEvent, System.nanoTime(), Collections.emptyMap());
        }

        traceWriter.finish();
    }

}
