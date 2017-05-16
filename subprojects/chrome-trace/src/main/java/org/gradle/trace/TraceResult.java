package org.gradle.trace;

import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceResult {
    private final Map<String, TraceEvent> events = new LinkedHashMap<>();

    public void count(String name, String metric, Map<String, Double> info) {
        count(name, metric, info, null);
    }

    public void count(String name, String metric, Map<String, Double> info, String colorName) {
        events.put(name, new CountEvent(metric, info, colorName));
    }

    public void start(String name, String category, long timestampNanos) {
        start(name, category, timestampNanos, null);
    }

    public void start(String name, String category, long timestampNanos, String colorName) {
        events.put(name, new DurationEvent(name, category, timestampNanos, new HashMap<>(), colorName));
    }

    public void finish(String name, long timestampNanos, Map<String, String> info) {
        DurationEvent event = (DurationEvent) events.get(name);
        if (event != null) {
            event.finished(timestampNanos);
            event.getInfo().putAll(info);
        }
    }

    public void writeEvents(File traceFile) {
        PrintWriter writer = getPrintWriter(traceFile);
        writer.println("{\n" +
                "  \"traceEvents\": [\n");

        Iterator<TraceEvent> itr = events.values().iterator();
        while (itr.hasNext()) {
            writer.print(itr.next().toString());
            writer.println(itr.hasNext() ? "," : "");
        }

        writer.println("],\n" +
                "  \"displayTimeUnit\": \"ns\",\n" +
                "  \"systemTraceEvents\": \"SystemTraceData\",\n" +
                "  \"otherData\": {\n" +
                "    \"version\": \"My Application v1.0\"\n" +
                "  }\n" +
                "}\n");
    }

    private PrintWriter getPrintWriter(File jsonFile) {
        try {
            return new PrintWriter(new FileWriter(jsonFile, true), true);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
