package org.gradle.trace;

public class TraceEvent {
    private final String name;
    private final String category;
    private final long threadId;
    private final long startTimestamp;
    private long endTimestamp;

    public TraceEvent(String name, String category, long timestampNanos) {
        this.name = name;
        this.category = category;
        this.threadId = Thread.currentThread().getId();
        this.startTimestamp = timestampNanos / 1000;
    }

    static TraceEvent started(String name, String category) {
        return started(name, category, getTimestamp());
    }

    static TraceEvent started(String name, String category, long timestamp) {
        return new TraceEvent(name, category, timestamp);
    }

    @Override
    public String toString() {
        long elapsed = endTimestamp - startTimestamp;
        return String.format("{\"name\": \"%s\", \"cat\": \"%s\", \"ph\": \"X\", \"pid\": 0, \"tid\": %d, \"ts\": %d, \"dur\": %d}", name, category, threadId, startTimestamp, elapsed);
    }

    private static long getTimestamp() {
        return (System.nanoTime());
    }

    public void finished() {
        this.endTimestamp = getTimestamp() / 1000;
    }
}
