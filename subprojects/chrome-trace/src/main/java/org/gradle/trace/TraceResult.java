package org.gradle.trace;

import org.gradle.api.invocation.Gradle;
import org.gradle.internal.UncheckedException;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceResult {
    private final Map<String, DurationEvent> events = new LinkedHashMap<>();
    private File tempTraceFile;
    private PrintWriter writer;

    public void count(String name, String metric, Map<String, Double> info) {
        count(name, metric, info, null);
    }

    public void count(String name, String metric, Map<String, Double> info, String colorName) {
        streamToTraceFile(new CountEvent(metric, info, colorName));
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
            streamToTraceFile(event);
        }
    }

    private void streamToTraceFile(TraceEvent event) {
        if (writer == null) {
            startTraceFile();
        } else {
            writer.print(',');
        }
        writer.print(event.toString());
    }

    private void startTraceFile() {
        try {
            tempTraceFile = Files.createTempFile("gradle-trace", ".html").toFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        copyResourceToTraceFile("/trace-header.html", false);
        writer = createPrintWriter();
        writer.println("{\n" +
                "  \"traceEvents\": [\n");
    }

    public void finalizeTraceFile(Gradle gradle) {
        for (DurationEvent unfinishedEvent : events.values()) {
            streamToTraceFile(unfinishedEvent);
        }

        if (tempTraceFile != null) {
            writer.println("],\n" +
                    "  \"displayTimeUnit\": \"ns\",\n" +
                    "  \"systemTraceEvents\": \"SystemTraceData\",\n" +
                    "  \"otherData\": {\n" +
                    "    \"version\": \"My Application v1.0\"\n" +
                    "  }\n" +
                    "}\n");
            writer.close();

            copyResourceToTraceFile("/trace-footer.html", true);

            File finalTraceFile = traceFile(gradle);
            tempTraceFile.renameTo(finalTraceFile);
            gradle.getRootProject().getLogger().lifecycle("Trace written to file://" + finalTraceFile.getAbsolutePath());

        }
    }

    private PrintWriter createPrintWriter() {
        try {
            return new PrintWriter(new FileWriter(tempTraceFile, true), true);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void copyResourceToTraceFile(String resourcePath, boolean append) {
        try (OutputStream out = new FileOutputStream(tempTraceFile, append);
             InputStream in = getClass().getResourceAsStream(resourcePath)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private File traceFile(Gradle gradle) {
        File traceFile = (File) gradle.getRootProject().findProperty("chromeTraceFile");
        if (traceFile == null) {
            traceFile = defaultTraceFile(gradle);
        }
        traceFile.getParentFile().mkdirs();
        return traceFile;
    }

    private File defaultTraceFile(Gradle gradle) {
        File buildDir = gradle.getRootProject().getBuildDir();
        return new File(buildDir, "trace/task-trace.html");
    }

}
