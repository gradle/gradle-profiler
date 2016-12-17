package org.gradle.trace;

import java.util.Iterator;
import java.util.Map;

public class DurationEvent implements TraceEvent {
    private final String name;
    private final String category;
    private final long threadId;
    private final long startTimestamp;
    private long endTimestamp;
    private final Map<String, String> info;
    private final String colorName;

    public DurationEvent(String name, String category, long timestampNanos, Map<String, String> info, String colorName) {
        this.name = name;
        this.category = category;
        this.threadId = Thread.currentThread().getId();
        this.startTimestamp = timestampNanos / 1000;
        this.info = info;
        this.colorName = colorName;
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

        String cname = "";
        if (colorName != null) {
            cname = String.format(", \"cname\": \"%s\"", colorName);
        }

        return String.format("{\"name\": \"%s\", \"cat\": \"%s\", \"ph\": \"X\", \"pid\": 0, \"tid\": %d, \"ts\": %d, \"dur\": %d, \"args\": %s %s}", name, category, threadId, startTimestamp, elapsed, args, cname);
    }

    public void finished(long timestampNanos) {
        this.endTimestamp = timestampNanos / 1000;
    }

}
