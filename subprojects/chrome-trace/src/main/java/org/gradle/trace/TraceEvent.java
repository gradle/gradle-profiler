package org.gradle.trace;

import java.util.Iterator;
import java.util.Map;

public class TraceEvent {
    private final String name;
    private final String category;
    private final long threadId;
    private final long startTimestamp;
    private long endTimestamp;
    private final Map<String, String> info;

    public TraceEvent(String name, String category, long timestampNanos, Map<String, String> info) {
        this.name = name;
        this.category = category;
        this.threadId = Thread.currentThread().getId();
        this.startTimestamp = timestampNanos / 1000;
        this.info = info;
    }

    static TraceEvent started(String name, String category, Map<String, String> info) {
        return started(name, category, getTimestamp(), info);
    }

    static TraceEvent started(String name, String category, long timestamp, Map<String, String> info) {
        return new TraceEvent(name, category, timestamp, info);
    }

    public Map<String, String> getInfo() {
        return info;
    }

    @Override
    public String toString() {
        long elapsed = endTimestamp - startTimestamp;

        StringBuilder s = new StringBuilder();
        s.append("{");
        Iterator<Map.Entry<String, String>> itr = info.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            s.append("\"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            s.append(itr.hasNext() ? "," : "");
        }
        s.append("}");
        String args = s.toString();

        return String.format("{\"name\": \"%s\", \"cat\": \"%s\", \"ph\": \"X\", \"pid\": 0, \"tid\": %d, \"ts\": %d, \"dur\": %d, \"args\": %s}", name, category, threadId, startTimestamp, elapsed, args);
    }

    private static long getTimestamp() {
        return (System.nanoTime());
    }

    public void finished() {
        this.endTimestamp = getTimestamp() / 1000;
    }
}
