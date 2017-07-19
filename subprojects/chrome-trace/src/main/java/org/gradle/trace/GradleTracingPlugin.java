package org.gradle.trace;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.trace.listener.BuildOperationListenerAdapter;
import org.gradle.trace.monitoring.GCMonitoring;
import org.gradle.trace.monitoring.SystemMonitoring;
import org.gradle.trace.util.TimeUtil;

import javax.inject.Inject;
import java.util.HashMap;

public class GradleTracingPlugin implements Plugin<Gradle> {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String PHASE_BUILD = "build duration";

    private final BuildRequestMetaData buildRequestMetaData;
    private final TraceResult traceResult = new TraceResult();
    private final SystemMonitoring systemMonitoring = new SystemMonitoring();
    private final GCMonitoring gcMonitoring = new GCMonitoring();
    private BuildOperationListenerAdapter buildOperationListener;

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
    }

    @Override
    public void apply(Gradle gradle) {
        traceResult.startTraceFile();
        systemMonitoring.start(traceResult);
        gcMonitoring.start(traceResult);
        buildOperationListener = BuildOperationListenerAdapter.create(gradle, traceResult);
        gradle.addListener(new TraceFinalizerAdapter(gradle));
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

            traceResult.start(PHASE_BUILD, CATEGORY_PHASE, TimeUtil.toNanoTime(buildRequestMetaData.getBuildTimeClock().getStartTime()));
            traceResult.finish(PHASE_BUILD, System.nanoTime(), new HashMap<>());
            traceResult.finalizeTraceFile(result.getGradle());

            gradle.removeListener(this);
        }
    }
}
