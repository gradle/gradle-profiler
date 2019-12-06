package org.gradle.trace;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.trace.stream.AsyncWriter;

public class AsynchronousTraceWriter {

    private final AsyncWriter<TraceEvent> eventQueue;
    private final File traceFile;

    public AsynchronousTraceWriter(File traceFile) {
        this.traceFile = traceFile;
        try {
            if (traceFile.exists()) {
                if (!traceFile.delete()) {
                    throw new RuntimeException("Unable to delete the old file " + traceFile.getAbsolutePath());
                }
            }
            if (!traceFile.createNewFile()) {
                throw new RuntimeException("Unable to create a file " + traceFile.getAbsolutePath());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        eventQueue = new AsyncWriter<>(traceFile, new TraceEventRenderer());
    }

    public void add(TraceEvent event) {
        eventQueue.append(event);
    }

    public void finish() {
        eventQueue.stop();
        if (System.getProperty("trace") == null) {
            return;
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
            writer.print(value.toString().replaceAll("/\\\\/g", "/\""));
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
