package org.gradle.trace;

import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.trace.stream.AsyncWriter;

import java.io.*;
import java.nio.file.Files;

public class AsynchronousTraceWriter {

    private final AsyncWriter<TraceEvent> eventQueue;
    private final File tempTraceFile;
    private final File traceFile;

    public AsynchronousTraceWriter(File traceFile) {
        this.traceFile = traceFile;
        try {
            tempTraceFile = Files.createTempFile("gradle-trace", ".html").toFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        eventQueue = new AsyncWriter<>(tempTraceFile, new TraceEventRenderer());
    }

    public void add(TraceEvent event) {
        eventQueue.append(event);
    }

    public void finish() {
        eventQueue.stop();
        if (System.getProperty("trace") == null) {
            return;
        }

        boolean success = tempTraceFile.renameTo(traceFile);
        if (!success) {
            throw new RuntimeException("Failed to move the trace file from a temporary location (" +
                tempTraceFile.getAbsolutePath() + ") to the final location (" + traceFile.getAbsolutePath() + ")");
        }
        Logging.getLogger(AsynchronousTraceWriter.class).lifecycle("Trace written to file://" + traceFile.getAbsolutePath());
    }

    private void copyResourceToTraceFile(String resourcePath, PrintWriter writer) {
        try (Reader in = new InputStreamReader(getClass().getResourceAsStream(resourcePath))) {
            char[] buffer = new char[1024];
            while (true) {
                int nread = in.read(buffer);
                if (nread < 0) {
                    break;
                }
                writer.write(buffer, 0, nread);
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

    private class TraceEventRenderer implements AsyncWriter.Renderer<TraceEvent> {
        boolean hasEvents;

        @Override
        public void header(PrintWriter writer) {
            copyResourceToTraceFile("/trace-header.html", writer);
            writer.println("{\n" +
                "  \"traceEvents\": [\n");
        }

        @Override
        public void write(TraceEvent value, PrintWriter writer) {
            if (hasEvents) {
                writer.print(',');
            } else {
                hasEvents = true;
            }
            writer.print(value.toString());
        }

        @Override
        public void footer(PrintWriter writer) {
            writer.println("],\n" +
                "  \"displayTimeUnit\": \"ns\",\n" +
                "  \"systemTraceEvents\": \"SystemTraceData\",\n" +
                "  \"otherData\": {\n" +
                "    \"version\": \"My Application v1.0\"\n" +
                "  }\n" +
                "}\n");

            copyResourceToTraceFile("/trace-footer.html", writer);
        }
    }
}
