package org.gradle.profiler.studio.plugin.client;

import com.google.common.base.Stopwatch;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioCacheCleanupCompleted;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.studio.plugin.system.GradleSyncResult;
import org.gradle.profiler.studio.plugin.system.GradleSystemListener;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.STOP_RECEIVING_EVENTS;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.getStartupSyncResult;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.startManualSync;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnBackgroundProcessesFinish;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnPostStartupActivities;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.waitOnPreviousGradleSyncFinish;

public class GradleProfilerClient {

    private static final Logger LOG = Logger.getInstance(GradleProfilerClient.class);

    private final Client client;
    private final Stopwatch startupStopwatch;
    private int syncCount;

    public GradleProfilerClient(Client client) {
        this.client = client;
        this.startupStopwatch = Stopwatch.createStarted();
    }

    public StudioRequest listenForSyncRequests(Project project, GradleSystemListener gradleSystemListener) {
        StudioRequest request = receiveNextEvent();
        waitOnPostStartupActivities(project);
        maybeImportProject(project);
        while (shouldHandleNextEvent(request)) {
            handleGradleProfilerRequest(request, project, gradleSystemListener);
            request = receiveNextEvent();
        }
        return request;
    }

    private void maybeImportProject(Project project) {
        GradleSettings gradleSettings = GradleSettings.getInstance(project);
        if (gradleSettings.getLinkedProjectsSettings().isEmpty()) {
            // We disabled auto import with 'external.system.auto.import.disabled=true', so we need to link and refresh the project manually
            VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
            GradleProjectImportUtil.linkAndRefreshGradleProject(projectDir.getPath(), project);
        }
    }

    private StudioRequest receiveNextEvent() {
        return client.receiveStudioRequest(Duration.ofDays(1));
    }

    private boolean shouldHandleNextEvent(StudioRequest request) {
        return request.getType() != EXIT_IDE && request.getType() != STOP_RECEIVING_EVENTS;
    }

    private void handleGradleProfilerRequest(StudioRequest request, Project project, GradleSystemListener gradleSystemListener) {
        switch (request.getType()) {
            case SYNC:
                handleSyncRequest(request, project, gradleSystemListener);
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

    private void handleSyncRequest(StudioRequest request, Project project, GradleSystemListener gradleSystemListener) {
        LOG.info("Received sync request with id: " + request.getId());
        boolean isStartup = syncCount++ == 0;

        // In some cases sync could happen before we trigger it,
        // for example when we open a project for the first time.
        // Also in some versions sync triggers first and then indexing and in others it's the opposite,
        // so we wait for sync finishing and then for background processes and then for sync again.
        if (isStartup) {
            waitOnPreviousGradleSyncFinish(project);
            waitOnBackgroundProcessesFinish(project);
            waitOnPreviousGradleSyncFinish(project);
        }

        LOG.info(String.format("[SYNC REQUEST %s] Sync has started%n", request.getId()));
        Stopwatch stopwatch = isStartup ? startupStopwatch : Stopwatch.createStarted();
        GradleSyncResult result = isStartup ? getStartupSyncResult(project, gradleSystemListener) : startManualSync(project, gradleSystemListener);
        LOG.info(String.format("[SYNC REQUEST %s] '%s'%n", request.getId(), result.getResult()));
        client.send(new StudioSyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS), result.getResult(), result.getErrorMessage()));
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
