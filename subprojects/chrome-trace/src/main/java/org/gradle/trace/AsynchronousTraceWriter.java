package org.gradle.trace;

import org.gradle.api.invocation.Gradle;
import org.gradle.internal.UncheckedException;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AsynchronousTraceWriter extends Thread {

    private final BlockingQueue<TraceEvent> eventQueue = new LinkedBlockingQueue<>();
    private File tempTraceFile;
    private PrintWriter writer;

    public AsynchronousTraceWriter() {
        super(AsynchronousTraceWriter.class.getSimpleName());
    }

    public void add(TraceEvent event) {
        eventQueue.add(event);
    }

    public void start() {
        super.start();
    }

    public void finish(Gradle gradle) {
        this.interrupt();
        try {
            this.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (writer != null && System.getProperty("trace") != null) {
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

    @Override
    public void run() {
        boolean finishing = false;
        while (true) {
            try {
                TraceEvent event = finishing ? eventQueue.poll() : eventQueue.take();
                if (event == null) {
                    break;
                } else {
                    streamToTraceFile(event);
                }
            } catch (InterruptedException e) {
                finishing = true;
            }
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
