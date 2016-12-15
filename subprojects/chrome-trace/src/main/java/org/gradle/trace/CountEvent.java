package org.gradle.trace;

import java.util.Iterator;
import java.util.Map;

public class CountEvent implements TraceEvent {
    private final String name;
    private final long timestampNanos;
    private final Map<String, Double> info;

    public CountEvent(String name, Map<String, Double> info) {
        this.name = name;
        this.timestampNanos = System.nanoTime();
        this.info = info;
    }

    @Override
    public String toString() {
        long timestamp = timestampNanos / 1000;

        StringBuilder s = new StringBuilder();
        s.append("{");
        Iterator<Map.Entry<String, Double>> itr = info.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, Double> entry = itr.next();
            s.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue()).append("");
            s.append(itr.hasNext() ? "," : "");
        }
        s.append("}");
        String args = s.toString();

        return String.format("{\"name\": \"%s\", \"ph\": \"C\", \"pid\": 0, \"tid\": %d, \"ts\": %d, \"args\": %s}", name, Thread.currentThread().getId(), timestamp, args);
    }
}
