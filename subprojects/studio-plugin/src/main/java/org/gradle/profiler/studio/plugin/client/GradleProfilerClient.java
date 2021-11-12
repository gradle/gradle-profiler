package org.gradle.profiler.studio.plugin.client;

import com.google.common.base.Stopwatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper;
import org.gradle.profiler.studio.plugin.system.GradleProfilerGradleSyncListener.GradleSyncResult;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.STOP_RECEIVING_EVENTS;

public class GradleProfilerClient {

    private static final Logger LOG = Logger.getInstance(GradleProfilerClient.class);
    public static final String PROFILER_PORT_PROPERTY = "gradle.profiler.port";

    private final Client client;
    private final AndroidStudioSystemHelper systemHelper;

    public GradleProfilerClient(Client client) {
        this.client = client;
        this.systemHelper = new AndroidStudioSystemHelper();
    }

    public void listenForSyncRequests(Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            StudioRequest request = receiveNextEvent();
            while (shouldHandleNextEvent(request)) {
                handleGradleProfilerRequest(request, project);
                request = receiveNextEvent();
            }
            client.disconnect();
            if (request.getType() == EXIT_IDE) {
                systemHelper.exit();
            }
        });
    }

    private StudioRequest receiveNextEvent() {
        return Client.INSTANCE.receiveStudioRequest(Duration.ofDays(1));
    }

    private boolean shouldHandleNextEvent(StudioRequest request) {
        return request.getType() != EXIT_IDE && request.getType() != STOP_RECEIVING_EVENTS;
    }

    private void handleGradleProfilerRequest(StudioRequest request, Project project) {
        switch (request.getType()) {
            case SYNC:
                handleSyncRequest(request, project);
                break;
            case EXIT_IDE:
                throw new IllegalArgumentException("Type: '" + request.getType() + "' should not be handled in 'handleGradleProfilerRequest()'.");
            default:
                throw new IllegalArgumentException("Unknown request type: '" + request.getType() + "'.");
        }
    }

    private void handleSyncRequest(StudioRequest request, Project project) {
        LOG.info("Received sync request with id: " + request.getId());

        // In some cases sync could happen before we trigger it,
        // for example when we open a project for the first time.
        systemHelper.waitOnPreviousGradleSyncFinish(project);
        systemHelper.waitOnBackgroundProcessesFinish(project);

        Stopwatch stopwatch = Stopwatch.createStarted();
        LOG.info(String.format("[SYNC REQUEST %s] Sync has started%n", request.getId()));
        GradleSyncResult result = systemHelper.doGradleSync(project);
        systemHelper.waitOnBackgroundProcessesFinish(project);
        LOG.info(String.format("[SYNC REQUEST %s] '%s': '%s'%n", request.getId(), result.getResult(), result.getErrorMessage().isEmpty() ? "no message" : result.getErrorMessage()));
        client.send(new StudioSyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS), result.getResult()));
    }

}
