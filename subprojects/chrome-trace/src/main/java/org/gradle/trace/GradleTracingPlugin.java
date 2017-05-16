package org.gradle.trace;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.OperatingSystemMXBean;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.UncheckedException;
import org.gradle.trace.listener.BuildOperationListenerAdapter;

import javax.inject.Inject;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GradleTracingPlugin implements Plugin<Gradle> {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String PHASE_BUILD = "build duration";
    private static final String GARBAGE_COLLECTION = "GARBAGE_COLLECTION";
    private final BuildRequestMetaData buildRequestMetaData;
    private final TraceResult traceResult = new TraceResult();
    private final OperatingSystemMXBean operatingSystemMXBean;
    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans;
    private int sysPollCount = 0;
    private final long jvmStartTime;
    private Map<GarbageCollectorMXBean, NotificationListener> gcNotificationListeners = new HashMap<>();
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final long maxHeap;
    private BuildOperationListenerAdapter buildOperationListener;

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
        this.operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        this.maxHeap = Runtime.getRuntime().maxMemory();
    }

    @Override
    public void apply(Gradle gradle) {
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

        for (GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
            NotificationEmitter emitter = (NotificationEmitter) garbageCollectorMXBean;
            NotificationListener gcNotificationListener = (notification, handback) -> {
                if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                    //get all the info and pretty print it
                    long duration = info.getGcInfo().getDuration();
                    String gctype = info.getGcAction();
                    Map<String, String> args = new HashMap<>();
                    args.put("type", gctype);

                    String colorName = "good";
                    if (gctype.equals("end of major GC")) {
                        colorName = "terrible";
                    }

                    traceResult.start("GC" + info.getGcInfo().getId(), GARBAGE_COLLECTION, toNanoTime(jvmStartTime + info.getGcInfo().getStartTime()), colorName);
                    traceResult.finish("GC" + info.getGcInfo().getId(), toNanoTime(jvmStartTime + info.getGcInfo().getEndTime()), args);
                    Map<String, MemoryUsage> pools = info.getGcInfo().getMemoryUsageAfterGc();
                    long unallocatedHeap = maxHeap;
                    HashMap<String, Double> gcInfo = new LinkedHashMap<>();
                    for (String pool : pools.keySet()) {
                        MemoryUsage usage = pools.get(pool);
                        gcInfo.put(pool, (double) usage.getUsed());
                        unallocatedHeap -= usage.getUsed();
                    }
                    gcInfo.put("unallocated", (double) unallocatedHeap);

                    traceResult.count("heap" + info.getGcInfo().getId(), "heap", gcInfo);
                }
            };
            emitter.addNotificationListener(gcNotificationListener, null, null);
            gcNotificationListeners.put(garbageCollectorMXBean, gcNotificationListener);
        }

        registerBuildOperationListener(gradle);

        gradle.getGradle().addListener(new JsonAdapter(gradle));
    }

    private void registerBuildOperationListener(Gradle gradle) {
        buildOperationListener = BuildOperationListenerAdapter.create(gradle, traceResult);
    }

    private void unregisterBuildOperationListener() {
        if (buildOperationListener != null) {
            buildOperationListener.remove();
            buildOperationListener = null;
        }
    }

    private class JsonAdapter extends BuildAdapter {
        private final Gradle gradle;

        private JsonAdapter(Gradle gradle) {
            this.gradle = gradle;
        }

        @Override
        public void buildFinished(BuildResult result) {
            scheduledExecutorService.shutdown();

            for (GarbageCollectorMXBean garbageCollectorMXBean : gcNotificationListeners.keySet()) {
                NotificationEmitter emitter = (NotificationEmitter) garbageCollectorMXBean;
                try {
                    emitter.removeNotificationListener(gcNotificationListeners.get(garbageCollectorMXBean));
                } catch (ListenerNotFoundException e) {
                }
            }

            unregisterBuildOperationListener();

            traceResult.start(PHASE_BUILD, CATEGORY_PHASE, toNanoTime(buildRequestMetaData.getBuildTimeClock().getStartTime()));
            traceResult.finish(PHASE_BUILD, System.nanoTime(), new HashMap<>());

            if (System.getProperty("trace") != null) {
                File traceFile = getTraceFile();

                copyResourceToFile("/trace-header.html", traceFile, false);
                traceResult.writeEvents(traceFile);
                copyResourceToFile("/trace-footer.html", traceFile, true);

                result.getGradle().getRootProject().getLogger().lifecycle("Trace written to file://" + traceFile.getAbsolutePath());
            }

            gradle.removeListener(this);
        }

        private void copyResourceToFile(String resourcePath, File traceFile, boolean append) {
            try (OutputStream out = new FileOutputStream(traceFile, append);
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

        private File getTraceFile() {
            File traceFile = (File) gradle.getRootProject().findProperty("chromeTraceFile");
            if (traceFile == null) {
                traceFile = defaultTraceFile();
            }
            traceFile.getParentFile().mkdirs();
            return traceFile;
        }

        private File defaultTraceFile() {
            File traceFile;
            File buildDir = gradle.getRootProject().getBuildDir();
            traceFile = new File(buildDir, "trace/task-trace.html");
            return traceFile;
        }
    }

    private long toNanoTime(long timeInMillis) {
        long elapsedMillis = System.currentTimeMillis() - timeInMillis;
        long elapsedNanos = TimeUnit.MILLISECONDS.toNanos(elapsedMillis);
        return System.nanoTime() - elapsedNanos;
    }
}
