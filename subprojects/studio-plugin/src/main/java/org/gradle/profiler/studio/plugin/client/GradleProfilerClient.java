package org.gradle.profiler.studio.plugin.client;

import com.google.common.base.Stopwatch;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioCacheCleanupCompleted;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.studio.plugin.system.GradleProfilerGradleSyncListener.GradleSyncResult;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.STOP_RECEIVING_EVENTS;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.doGradleSync;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnBackgroundProcessesFinish;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnPreviousGradleSyncFinish;

public class GradleProfilerClient {

    private static final Logger LOG = Logger.getInstance(GradleProfilerClient.class);

    private final Client client;

    public GradleProfilerClient(Client client) {
        this.client = client;
    }

    public StudioRequest listenForSyncRequests(Project project) {
        StudioRequest request = receiveNextEvent();
        while (shouldHandleNextEvent(request)) {
            handleGradleProfilerRequest(request, project);
            request = receiveNextEvent();
        }
        return request;
    }

    private StudioRequest receiveNextEvent() {
        return client.receiveStudioRequest(Duration.ofDays(1));
    }

    private boolean shouldHandleNextEvent(StudioRequest request) {
        return request.getType() != EXIT_IDE && request.getType() != STOP_RECEIVING_EVENTS;
    }

    private void handleGradleProfilerRequest(StudioRequest request, Project project) {
        switch (request.getType()) {
            case SYNC:
                handleSyncRequest(request, project);
                break;
            case CLEANUP_CACHE:
                cleanupCache(request);
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
        waitOnPreviousGradleSyncFinish(project);
        waitOnBackgroundProcessesFinish(project);

        Stopwatch stopwatch = Stopwatch.createStarted();
        LOG.info(String.format("[SYNC REQUEST %s] Sync has started%n", request.getId()));
        GradleSyncResult result = doGradleSync(project);
        waitOnBackgroundProcessesFinish(project);
        LOG.info(String.format("[SYNC REQUEST %s] '%s': '%s'%n", request.getId(), result.getResult(), result.getErrorMessage().isEmpty() ? "no message" : result.getErrorMessage()));
        client.send(new StudioSyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS), result.getResult()));
    }

    /**
     * This code is similar to one in com.intellij.ide.InvalidateCacheService in IntelliJ Community project,
     * it just does not make a dialog to restart IDE.
     * @param request
     */
    private void cleanupCache(StudioRequest request) {
        CachesInvalidator.EP_NAME.getExtensionList().forEach(it -> {
            try {
                it.invalidateCaches();
            } catch (Throwable t) {
                LOG.warn("Failed to invalidate caches with " + it.getClass().getName() + ". " + t.getMessage(), t);
            }
        });
        client.send(new StudioCacheCleanupCompleted(request.getId()));
    }

}
