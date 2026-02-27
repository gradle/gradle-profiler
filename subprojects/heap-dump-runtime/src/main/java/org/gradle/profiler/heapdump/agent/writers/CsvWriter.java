package org.gradle.profiler.heapdump.agent.writers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class CsvWriter {

    public static void write(Path directory, String baseFilename, int heapDumpCounter, String strategy, String additionalData, long pid) {
        try {
            // Create individual CSV file matching the heap dump filename
            Path csvPath = directory.resolve(baseFilename + ".csv");

            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            StringBuilder csv = new StringBuilder();

            // Write header
            csv.append("pid,index,strategy,metadata,heap_used_bytes,heap_committed_bytes,heap_max_bytes,nonheap_used_bytes,nonheap_committed_bytes,nonheap_max_bytes\n");

            // Write data row
            csv.append(pid).append(",");
            csv.append(heapDumpCounter).append(",");
            csv.append(strategy).append(",");
            csv.append(additionalData != null ? escapeCsvField(additionalData) : "").append(",");
            csv.append(heapUsage.getUsed()).append(",");
            csv.append(heapUsage.getCommitted()).append(",");
            csv.append(heapUsage.getMax()).append(",");
            csv.append(nonHeapUsage.getUsed()).append(",");
            csv.append(nonHeapUsage.getCommitted()).append(",");
            csv.append(nonHeapUsage.getMax()).append("\n");

            Files.writeString(csvPath, csv.toString());

            System.out.println("HEAP_DUMP_EXECUTOR: CSV data written to " + csvPath);
        } catch (Exception e) {
            System.err.println("HEAP_DUMP_EXECUTOR: Failed to write CSV data: " + e.getMessage());
        }
    }

    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // Quote field if it contains comma, quote, or newline
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
