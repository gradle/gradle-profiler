package org.gradle.trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import com.sun.management.OperatingSystemMXBean;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.execution.internal.TaskOperationDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class GradleTracingPlugin implements Plugin<Gradle> {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String CATEGORY_OPERATION = "BUILD_OPERATION";
    private static final String PHASE_BUILD = "build duration";
    private final BuildRequestMetaData buildRequestMetaData;
    private final Map<String, TraceEvent> events = new LinkedHashMap<>();
    private final OperatingSystemMXBean operatingSystemMXBean;
    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans;
    private int sysPollCount = 0;

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
        this.operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }

    private void start(String name, String category, long timestampNanos) {
        events.put(name, DurationEvent.started(name, category, timestampNanos, new HashMap<>()));
    }

    private void finish(String name, long timestampNanos, Map<String, String> info) {
        DurationEvent event = (DurationEvent) events.get(name);
        if (event != null) {
            event.finished(timestampNanos);
            event.getInfo().putAll(info);
        }
    }

    private void count(String name, String metric, Map<String, Double> info) {
        events.put(name, new CountEvent(metric, info));
    }

    @Override
    public void apply(Gradle gradle) {
        ListenerManager globalListenerManager = getGlobalListenerManager(gradle);

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            HashMap<String, Double> cpuStats = new HashMap<>();
            cpuStats.put("cpu", operatingSystemMXBean.getProcessCpuLoad()*100);
            count("cpu" + sysPollCount, "cpu", cpuStats);

            for (GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
                com.sun.management.GarbageCollectorMXBean gcBean = (com.sun.management.GarbageCollectorMXBean) garbageCollectorMXBean;
                Map<String, MemoryUsage> pools = gcBean.getLastGcInfo().getMemoryUsageAfterGc();
                HashMap<String, Double> gcInfo = new HashMap<>();
                for (String pool : pools.keySet()) {
                    MemoryUsage usage = pools.get(pool);
                    gcInfo.put(pool, (double) usage.getUsed());
                }
                count("heap" + sysPollCount, "heap", gcInfo);
            }
            sysPollCount++;
        }, 0, 1, TimeUnit.SECONDS);

        globalListenerManager.addListener( new InternalBuildListener() {
                @Override
                public void started(BuildOperationInternal operation, OperationStartEvent startEvent) {
                    start(operation.getDisplayName(), CATEGORY_OPERATION, toNanoTime(startEvent.getStartTime()));
                }

                @Override
                public void finished(BuildOperationInternal operation, OperationResult result) {
                    Map<String, String> info = new HashMap<>();
                    if (operation.getOperationDescriptor() instanceof TaskOperationDescriptor) {
                        TaskOperationDescriptor taskDescriptor = (TaskOperationDescriptor) operation.getOperationDescriptor();
                        TaskInternal task = taskDescriptor.getTask();
                        info.put("type", task.getClass().getSimpleName());
                        info.put("didWork", String.valueOf(task.getDidWork()));
                    }
                    finish(operation.getDisplayName(), toNanoTime(result.getEndTime()), info);
                }
            });
        gradle.getGradle().addListener(new JsonAdapter(gradle));
    }

    private class JsonAdapter extends BuildAdapter {
        private final Gradle gradle;

        private JsonAdapter(Gradle gradle) {
            this.gradle = gradle;
        }

        @Override
        public void buildFinished(BuildResult result) {
            scheduledExecutorService.shutdown();
            DurationEvent overallBuild = DurationEvent.started(PHASE_BUILD, CATEGORY_PHASE, toNanoTime(buildRequestMetaData.getBuildTimeClock().getStartTime()), new HashMap<>());
            overallBuild.finished(System.nanoTime());

            events.put(PHASE_BUILD, overallBuild);

            File traceFile = getTraceFile();

            copyResourceToFile("/trace-header.html", traceFile, false);
            writeEvents(traceFile);
            copyResourceToFile("/trace-footer.html", traceFile, true);

            result.getGradle().getRootProject().getLogger().lifecycle("Trace written to file://" + traceFile.getAbsolutePath());
        }

        private void copyResourceToFile(String resourcePath, File traceFile, boolean append) {
            try(OutputStream out = new FileOutputStream(traceFile, append);
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

    private void writeEvents(File traceFile) {
        PrintWriter writer = getPrintWriter(traceFile);
        writer.println("{\n" +
                "  \"traceEvents\": [\n");

        Iterator<TraceEvent> itr = events.values().iterator();
        while (itr.hasNext()) {
            writer.print(itr.next().toString());
            writer.println(itr.hasNext() ? "," : "");
        }

        writer.println("],\n" +
                "  \"displayTimeUnit\": \"ns\",\n" +
                "  \"systemTraceEvents\": \"SystemTraceData\",\n" +
                "  \"otherData\": {\n" +
                "    \"version\": \"My Application v1.0\"\n" +
                "  }\n" +
                "}\n");
    }

    private PrintWriter getPrintWriter(File jsonFile) {
        try {
            return new PrintWriter(new FileWriter(jsonFile, true), true);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private long toNanoTime(long timeInMillis) {
        long elapsedMillis = System.currentTimeMillis() - timeInMillis;
        long elapsedNanos = TimeUnit.MILLISECONDS.toNanos(elapsedMillis);
        return System.nanoTime() - elapsedNanos;
    }


    private ListenerManager getGlobalListenerManager( final Gradle gradle ) {
        try {
            GradleInternal gradleInternal = (GradleInternal) gradle;
            ServiceRegistry services = gradleInternal.getServices();
            GradleLauncherFactory gradleLauncherFactory = services.get(GradleLauncherFactory.class);
            Field field = DefaultGradleLauncherFactory.class.getDeclaredField("globalListenerManager");
            field.setAccessible(true);
            return (ListenerManager) field.get(gradleLauncherFactory);
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
