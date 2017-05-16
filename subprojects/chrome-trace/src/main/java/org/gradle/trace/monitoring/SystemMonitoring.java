package org.gradle.trace.monitoring;

import com.sun.management.OperatingSystemMXBean;
import org.gradle.trace.TraceResult;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemMonitoring {

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final OperatingSystemMXBean operatingSystemMXBean;
    private int sysPollCount = 0;

    public SystemMonitoring() {
        this.operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public void start(TraceResult traceResult) {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            HashMap<String, Double> cpuStats = new HashMap<>();
            double pcpu = operatingSystemMXBean.getProcessCpuLoad() * 100;
            double scpu = operatingSystemMXBean.getSystemCpuLoad() * 100;

            if (!Double.isNaN(pcpu) && !Double.isNaN(scpu)) {
                cpuStats.put("process_cpu_used", pcpu);
                cpuStats.put("non_process_cpu_used", scpu - pcpu);

                traceResult.count("cpu" + sysPollCount, "cpu", cpuStats);
            }

            sysPollCount++;
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduledExecutorService.shutdown();
    }
}
