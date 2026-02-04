package org.gradle.profiler.lifecycle.agent;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.gradle.profiler.lifecycle.agent.strategy.HeapDumpStrategy;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates heap dumps at specific points in the Gradle build lifecycle.
 * Uses composition with HeapDumpStrategy to determine when and how to intercept.
 */
public class HeapDumpInterceptor {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private volatile boolean heapDumpCreated = false;
    private final String outputPath;
    private final HeapDumpStrategy metadata;

    public HeapDumpInterceptor(String outputPath, HeapDumpStrategy metadata) {
        this.outputPath = outputPath;
        this.metadata = metadata;
    }

    /**
     * Called when the interception point is reached. Creates a heap dump if not already done.
     */
    public void intercept() {
        // Only create one heap dump per build per strategy
        if (heapDumpCreated) {
            return;
        }

        synchronized (this) {
            if (heapDumpCreated) {
                return;
            }

            System.out.println("--------------------------------------------------------------------------------");
            System.out.println(metadata.getInterceptionMessage() + " - Creating Heap Dump");
            System.out.println("--------------------------------------------------------------------------------");

            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String fileName = String.format("%s-%s.hprof", metadata.getFilePrefix(), timestamp);
                String fullPath = Paths.get(outputPath, fileName).toAbsolutePath().toString();

                System.out.println("Heap dump location: " + fullPath);
                System.out.println("Creating heap dump...");

                long startTime = System.currentTimeMillis();
                createHeapDump(fullPath, true);
                long duration = System.currentTimeMillis() - startTime;

                System.out.println("Heap dump created successfully in " + duration + "ms");
                System.out.println("File: " + fullPath);
                System.out.println("--------------------------------------------------------------------------------");

                heapDumpCreated = true;
            } catch (Exception e) {
                System.err.println("ERROR: Failed to create heap dump");
                e.printStackTrace();
            }
        }
    }

    public String getTargetMethodName() {
        return metadata.getTargetMethodName();
    }

    public String getTargetMethodDescriptor() {
        return metadata.getTargetMethodDescriptor();
    }

    /**
     * Creates a heap dump using the HotSpot diagnostic MXBean.
     *
     * @param filePath the full path where the heap dump should be saved
     * @param liveObjectsOnly if true, only dump live objects; if false, dump all objects
     * @throws IOException if the heap dump cannot be created
     */
    private static void createHeapDump(String filePath, boolean liveObjectsOnly) throws IOException {
        try {
            HotSpotDiagnosticMXBean mxBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            mxBean.dumpHeap(filePath, liveObjectsOnly);
        } catch (IOException e) {
            throw new IOException("Failed to create heap dump at: " + filePath, e);
        }
    }

    /**
     * Utility method to get memory information at the time of the dump.
     * Can be called for additional diagnostics.
     *
     * @return a string with current memory statistics
     */
    public static String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        return String.format(
            "Memory: Used=%dMB, Free=%dMB, Total=%dMB, Max=%dMB",
            usedMemory / (1024 * 1024),
            freeMemory / (1024 * 1024),
            totalMemory / (1024 * 1024),
            maxMemory / (1024 * 1024)
        );
    }
}
