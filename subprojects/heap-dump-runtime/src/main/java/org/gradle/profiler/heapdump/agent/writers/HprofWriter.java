package org.gradle.profiler.heapdump.agent.writers;

import com.sun.management.HotSpotDiagnosticMXBean;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;

public class HprofWriter {

    public static void write(Path directory, String baseFilename) throws Exception {
        Path filePath = directory.resolve(baseFilename + ".hprof");

        System.out.println("HEAP_DUMP_EXECUTOR: Creating heap dump at " + filePath);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean.class
        );
        mxBean.dumpHeap(filePath.toString(), true);
        System.out.println("HEAP_DUMP_EXECUTOR: Heap dump created successfully");
    }
}
