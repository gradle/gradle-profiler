package org.gradle.trace.monitoring;

import com.sun.management.GarbageCollectionNotificationInfo;
import org.gradle.trace.TraceResult;
import org.gradle.trace.util.TimeUtil;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GCMonitoring {
    private static final String GARBAGE_COLLECTION = "GARBAGE_COLLECTION";

    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans;
    private final long jvmStartTime;
    private final long maxHeap;

    private final Map<GarbageCollectorMXBean, NotificationListener> gcNotificationListeners = new HashMap<>();

    public GCMonitoring() {
        this.garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        this.maxHeap = Runtime.getRuntime().maxMemory();
    }

    public void start(TraceResult traceResult) {
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

                    traceResult.start("GC" + info.getGcInfo().getId(), GARBAGE_COLLECTION, TimeUtil.toNanoTime(jvmStartTime + info.getGcInfo().getStartTime()), colorName);
                    traceResult.finish("GC" + info.getGcInfo().getId(), TimeUtil.toNanoTime(jvmStartTime + info.getGcInfo().getEndTime()), args);
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
    }

    public void stop() {
        for (GarbageCollectorMXBean garbageCollectorMXBean : gcNotificationListeners.keySet()) {
            NotificationEmitter emitter = (NotificationEmitter) garbageCollectorMXBean;
            try {
                emitter.removeNotificationListener(gcNotificationListeners.get(garbageCollectorMXBean));
            } catch (ListenerNotFoundException e) {
            }
        }
    }
}
