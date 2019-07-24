package org.gradle.trace;

import static org.gradle.trace.util.ReflectionUtil.invokerGetter;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.trace.listener.BuildOperationListenerAdapter;
import org.gradle.trace.monitoring.GCMonitoring;
import org.gradle.trace.monitoring.SystemMonitoring;
import org.gradle.trace.util.TimeUtil;

import java.io.File;
import java.util.HashMap;

public class GradleTracingPlugin {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String PHASE_BUILD = "build duration";

    private final BuildRequestMetaData buildRequestMetaData;
    private final TraceResult traceResult;
    private final SystemMonitoring systemMonitoring = new SystemMonitoring();
    private final GCMonitoring gcMonitoring = new GCMonitoring();
    private final BuildOperationListenerAdapter buildOperationListener;

    private GradleTracingPlugin(GradleInternal gradle, File traceFile) {
        this.buildRequestMetaData = gradle.getServices().get(BuildRequestMetaData.class);
        traceResult = new TraceResult(traceFile);
        systemMonitoring.start(traceResult);
        gcMonitoring.start(traceResult);
        buildOperationListener = BuildOperationListenerAdapter.create(gradle, traceResult);
        gradle.addListener(new TraceFinalizerAdapter(gradle));
    }

    @SuppressWarnings("unused")
    public static void start(GradleInternal gradle, File traceFile) {
        new GradleTracingPlugin(gradle, traceFile);
    }

    private class TraceFinalizerAdapter extends BuildAdapter {
        private final Gradle gradle;

        private TraceFinalizerAdapter(Gradle gradle) {
            this.gradle = gradle;
        }

        @Override
        public void buildFinished(BuildResult result) {
            systemMonitoring.stop();
            gcMonitoring.stop();
            buildOperationListener.remove();

            traceResult.start(PHASE_BUILD, CATEGORY_PHASE, TimeUtil.toNanoTime(getStartTime()));
            traceResult.finish(PHASE_BUILD, System.nanoTime(), new HashMap<>());
            traceResult.finalizeTraceFile();

            gradle.removeListener(this);
        }

        private long getStartTime() {
            try {
                return buildRequestMetaData.getStartTime();
            } catch (NoSuchMethodError e) {
                return (long) invokerGetter(invokerGetter(buildRequestMetaData, "getBuildTimeClock"), "getStartTime");
            }
        }
    }
}
