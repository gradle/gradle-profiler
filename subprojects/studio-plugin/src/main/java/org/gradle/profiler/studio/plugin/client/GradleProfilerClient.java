package org.gradle.profiler.studio.plugin.client;

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.base.Stopwatch;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioCacheCleanupCompleted;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult;
import org.gradle.profiler.studio.plugin.system.GradleProfilerGradleSyncListener.GradleSyncResult;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SKIPPED;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SKIPPED_OUT_OF_DATE;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.UNKNOWN;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.STOP_RECEIVING_EVENTS;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.doGradleSync;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnBackgroundProcessesFinish;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnPostStartupActivities;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnPreviousGradleSyncFinish;

public class GradleProfilerClient {

    private static final Logger LOG = Logger.getInstance(GradleProfilerClient.class);

    private final Client client;
    private final Stopwatch startupStopwatch;
    private final AtomicInteger syncCount;

    public GradleProfilerClient(Client client) {
        this.client = client;
        this.startupStopwatch = Stopwatch.createStarted();
        this.syncCount = new AtomicInteger();
    }

    public StudioRequest listenForSyncRequests(Project project) {
        StudioRequest request = receiveNextEvent();
        waitOnPostStartupActivities(project);
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
        boolean isStartup = syncCount.getAndIncrement() == 0;

        // In some cases sync could happen before we trigger it,
        // for example when we open a project for the first time.
        waitOnPreviousGradleSyncFinish(project);
        waitOnBackgroundProcessesFinish(project);

        LOG.info(String.format("[SYNC REQUEST %s] Sync has started%n", request.getId()));
        Stopwatch stopwatch = isStartup ? startupStopwatch : Stopwatch.createStarted();
        GradleSyncResult result = isStartup ? getStartupSyncResult(project) : startManualSync(project);
        LOG.info(String.format("[SYNC REQUEST %s] '%s': '%s'%n", request.getId(), result.getResult(), result.getErrorMessage()));
        client.send(new StudioSyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS), result.getResult(), result.getErrorMessage()));
    }

    private GradleSyncResult startManualSync(Project project) {
        GradleSyncResult result = doGradleSync(project);
        waitOnBackgroundProcessesFinish(project);
        return result;
    }

    /**
     * Gets the result of startup sync by checking if there was already any sync done before. Otherwise, it starts a new sync.
     */
    private GradleSyncResult getStartupSyncResult(Project project) {
        ProjectSystemSyncManager.SyncResult lastSyncResult = ProjectSystemUtil.getSyncManager(project).getLastSyncResult();
        if (lastSyncResult == UNKNOWN) {
            // Sync was not run before, we need to run it manually
            return startManualSync(project);
        }
        GradleSyncResult result;
        if (lastSyncResult.isSuccessful() && (lastSyncResult == SKIPPED || lastSyncResult == SKIPPED_OUT_OF_DATE)) {
            result = new GradleSyncResult(StudioSyncRequestResult.SKIPPED, "");
        } else if (lastSyncResult.isSuccessful()) {
            result = new GradleSyncResult(StudioSyncRequestResult.SUCCEEDED, "");
        } else  {
            result = new GradleSyncResult(StudioSyncRequestResult.FAILED, "Startup failure");
        }
        return result;
    }

    /**
     * This code is similar to one in com.intellij.ide.InvalidateCacheService in IntelliJ Community project,
     * it just does not make a dialog to restart IDE.
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
