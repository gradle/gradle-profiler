package org.gradle.trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
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

public class GradleTracingPlugin implements Plugin<Gradle> {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String CATEGORY_OPERATION = "BUILD_OPERATION";
    private static final String PHASE_BUILD = "build duration";
    private final BuildRequestMetaData buildRequestMetaData;
    private final Map<String, TraceEvent> events = new LinkedHashMap<>();

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
    }

    private void start(String name, String category, long timestampNanos) {
        events.put(name, TraceEvent.started(name, category, timestampNanos, new HashMap<>()));
    }

    private TraceEvent finish(String name, long timestampNanos, Map<String, String> info) {
        TraceEvent event = events.get(name);
        if (event != null) {
            event.finished(timestampNanos);
            event.getInfo().putAll( info );
        }
        return event;
    }

    @Override
    public void apply(Gradle gradle) {
        ListenerManager globalListenerManager = getGlobalListenerManager(gradle);
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
            TraceEvent overallBuild = TraceEvent.started(PHASE_BUILD, CATEGORY_PHASE, toNanoTime(buildRequestMetaData.getBuildTimeClock().getStartTime()), new HashMap<>());
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
