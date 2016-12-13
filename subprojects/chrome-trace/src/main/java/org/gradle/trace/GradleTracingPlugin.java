package org.gradle.trace;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.*;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;

import javax.inject.Inject;
import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GradleTracingPlugin implements Plugin<Gradle> {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String CATEGORY_EVALUATE = "PROJECT_EVALUATE";
    private static final String CATEGORY_RESOLVE = "CONFIGURATION_RESOLVE";
    private static final String CATEGORY_TASK = "TASK_EXECUTE";
    private static final String CATEGORY_OPERATION = "BUILD_OPERATION";
    private static final String PHASE_BUILD = "build duration";
    private static final String PHASE_BUILD_TASK_GRAPH = "build task graph";
    private final BuildRequestMetaData buildRequestMetaData;
    private final Map<String, TraceEvent> events = new LinkedHashMap<>();

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
    }

    private void start(String name, String category) {
        events.put(name, TraceEvent.started(name, category));
    }

    private void finish(String name) {
        TraceEvent event = events.get(name);
        if (event != null) {
            event.finished();
        }
    }

    @Override
    public void apply(Gradle gradle) {
        gradle.addListener(new TaskExecutionListener() {
            @Override
            public void beforeExecute(Task task) {
                start(task.getPath(), CATEGORY_TASK);
            }

            @Override
            public void afterExecute(Task task, TaskState taskState) {
                finish(task.getPath());
            }
        });

        gradle.addListener(new DependencyResolutionListener() {
            @Override
            public void beforeResolve(ResolvableDependencies resolvableDependencies) {
                start(resolvableDependencies.getPath(), CATEGORY_RESOLVE);
            }

            @Override
            public void afterResolve(ResolvableDependencies resolvableDependencies) {
                finish(resolvableDependencies.getPath());
            }
        });

        gradle.addListener(new ProjectEvaluationListener() {
            @Override
            public void beforeEvaluate(Project project) {
                start(project.getPath(), CATEGORY_EVALUATE);
            }

            @Override
            public void afterEvaluate(Project project, ProjectState projectState) {
                finish(project.getPath());
            }
        });

        gradle.addListener(new InternalBuildListener() {
            @Override
            public void started(BuildOperationInternal buildOperationInternal, OperationStartEvent operationStartEvent) {
                start(buildOperationInternal.getDisplayName(), CATEGORY_OPERATION);
            }

            @Override
            public void finished(BuildOperationInternal buildOperationInternal, OperationResult operationResult) {
                finish(buildOperationInternal.getDisplayName());
            }
        });

        gradle.getTaskGraph().whenReady(taskExecutionGraph -> finish(PHASE_BUILD_TASK_GRAPH));

        gradle.getGradle().addListener(new JsonAdapter(gradle));
    }

    private class JsonAdapter extends BuildAdapter {
        private final Gradle gradle;

        private JsonAdapter(Gradle gradle) {
            this.gradle = gradle;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            start(PHASE_BUILD_TASK_GRAPH, CATEGORY_PHASE);
            System.out.println("START TASK GRAPH");
        }

        @Override
        public void buildFinished(BuildResult result) {
            TraceEvent overallBuild = TraceEvent.started(PHASE_BUILD, CATEGORY_PHASE, toNanoTime(buildRequestMetaData.getBuildTimeClock().getStartTime()));
            overallBuild.finished();
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
}
