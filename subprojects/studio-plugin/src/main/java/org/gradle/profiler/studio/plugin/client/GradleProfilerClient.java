package org.gradle.profiler.studio.plugin.client;

import com.google.common.base.Stopwatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.studio.plugin.system.GradleProfilerGradleSyncListener.GradleSyncResult;
import org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT;

public class GradleProfilerClient {

    private static final Logger LOG = Logger.getInstance(GradleProfilerClient.class);
    public static final String PROFILER_PORT_PROPERTY = "gradle.profiler.port";

    private final AndroidStudioSystemHelper systemHelper = new AndroidStudioSystemHelper();

    public void connectToProfilerAsync(Project project) {
        if (System.getProperty(PROFILER_PORT_PROPERTY) == null) {
            return;
        }

        int port = Integer.parseInt(System.getProperty(PROFILER_PORT_PROPERTY));
        Client.INSTANCE.connect(port);
        LOG.info("Connected to port: " + System.getProperty(PROFILER_PORT_PROPERTY));

        Client.INSTANCE.listenAsync(it -> {
            StudioRequest request;
            while ((request = it.receiveStudioRequest(Duration.ofDays(1))).getType() != EXIT) {
                handleGradleProfilerRequest(request, project);
            }
            systemHelper.exit();
        });
    }

    private void handleGradleProfilerRequest(StudioRequest request, Project project) {
        switch (request.getType()) {
            case SYNC:
                handleSyncRequest(request, project);
                break;
            case EXIT:
                throw new IllegalArgumentException("Type: '" + request.getType() + "' should not be handled in 'handleGradleProfilerRequest()'.");
            default:
                throw new IllegalArgumentException("Unknown request type: '" + request.getType() + "'.");
        }
    }

    private void handleSyncRequest(StudioRequest request, Project project) {
        LOG.info("Received sync request with id: " + request.getId());

        // In some cases sync could happen before we trigger it,
        // for example when we open a project for the first time.
        systemHelper.waitOnBackgroundProcessesFinish(project);
        systemHelper.waitOnPreviousGradleSyncFinish(project);

        Stopwatch stopwatch = Stopwatch.createStarted();
        LOG.info(String.format("[SYNC REQUEST %s] Sync has started%n", request.getId()));
        GradleSyncResult result = systemHelper.doGradleSync(project);
        systemHelper.waitOnBackgroundProcessesFinish(project);
        LOG.info(String.format("[SYNC REQUEST %s] '%s': '%s'%n", request.getId(), result.getResult(), result.getErrorMessage().isEmpty() ? "no message" : result.getErrorMessage()));
        Client.INSTANCE.send(new StudioSyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS), result.getResult()));
    }

}
