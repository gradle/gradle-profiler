package org.gradle.profiler.heapdump.agent.writers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class TxtWriter {

    public static void write(Path directory, String baseFilename, int heapDumpCounter, String strategy, String additionalData, long pid) {
        try {
            Path statsPath = directory.resolve(baseFilename + ".stats.txt");

            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            StringBuilder stats = new StringBuilder();
            stats.append("Heap Dump Statistics\n");
            stats.append("====================\n\n");
            stats.append("Timestamp: ").append(Instant.now()).append("\n");
            stats.append("Counter:   ").append(heapDumpCounter).append("\n");
            stats.append("Strategy:  ").append(strategy).append("\n");
            if (additionalData != null && !additionalData.isEmpty()) {
                stats.append("Metadata:  ").append(additionalData).append("\n");
            }
            stats.append("PID:       ").append(pid).append("\n");
            stats.append("Heap Dump: ").append(baseFilename).append(".hprof\n\n");

            stats.append("Heap Memory:\n");
            stats.append("  Used:      ").append(formatBytes(heapUsage.getUsed())).append("\n");
            stats.append("  Committed: ").append(formatBytes(heapUsage.getCommitted())).append("\n");
            stats.append("  Max:       ").append(formatBytes(heapUsage.getMax())).append("\n\n");

            stats.append("Non-Heap Memory:\n");
            stats.append("  Used:      ").append(formatBytes(nonHeapUsage.getUsed())).append("\n");
            stats.append("  Committed: ").append(formatBytes(nonHeapUsage.getCommitted())).append("\n");
            stats.append("  Max:       ").append(formatBytes(nonHeapUsage.getMax())).append("\n");

            Files.writeString(statsPath, stats.toString());
            System.out.println("HEAP_DUMP_EXECUTOR: Heap statistics written to " + statsPath);
        } catch (Exception e) {
            System.err.println("HEAP_DUMP_EXECUTOR: Failed to write heap statistics: " + e.getMessage());
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "N/A";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB (%d bytes)", bytes / Math.pow(1024, exp), pre, bytes);
    }
}
