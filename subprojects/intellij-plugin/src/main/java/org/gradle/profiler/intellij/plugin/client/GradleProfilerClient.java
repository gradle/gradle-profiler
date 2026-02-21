package org.gradle.profiler.intellij.plugin.client;

import com.google.common.base.Stopwatch;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.IdeCacheCleanupCompleted;
import org.gradle.profiler.client.protocol.messages.IdeRequest;
import org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted;
import org.gradle.profiler.intellij.plugin.system.GradleSyncHelper;
import org.gradle.profiler.intellij.plugin.system.GradleSyncResult;
import org.gradle.profiler.intellij.plugin.system.GradleSystemListener;
import org.gradle.profiler.intellij.plugin.system.IdeSystemHelper;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.gradle.profiler.client.protocol.messages.IdeRequest.IdeRequestType.EXIT_IDE;
import static org.gradle.profiler.client.protocol.messages.IdeRequest.IdeRequestType.STOP_RECEIVING_EVENTS;

public class GradleProfilerClient {

    private static final Logger LOG = Logger.getInstance(GradleProfilerClient.class);

    private final Client client;
    private final Stopwatch startupStopwatch;
    private int syncCount;

    public GradleProfilerClient(Client client) {
        this.client = client;
        this.startupStopwatch = Stopwatch.createStarted();
    }

    public IdeRequest listenForSyncRequests(Project project, GradleSystemListener gradleSystemListener) {
        IdeRequest request = receiveNextEvent();
        IdeSystemHelper.waitOnPostStartupActivities(project);
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

    private IdeRequest receiveNextEvent() {
        return client.receiveIdeRequest(Duration.ofDays(1));
    }

    private boolean shouldHandleNextEvent(IdeRequest request) {
        return request.getType() != EXIT_IDE && request.getType() != STOP_RECEIVING_EVENTS;
    }

    private void handleGradleProfilerRequest(IdeRequest request, Project project, GradleSystemListener gradleSystemListener) {
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

    private void handleSyncRequest(IdeRequest request, Project project, GradleSystemListener gradleSystemListener) {
        LOG.info("Received sync request with id: " + request.getId());
        boolean isStartup = syncCount++ == 0;

        // In some cases sync could happen before we trigger it (e.g. when opening a project for the first time).
        // Also in some versions sync triggers first and then indexing and in others it's the opposite,
        // so we wait for sync to finish, then for background processes, then for sync again.
        if (isStartup) {
            GradleSyncHelper.waitOnPreviousGradleSyncFinish(gradleSystemListener);
            IdeSystemHelper.waitOnBackgroundProcessesFinish(project);
            GradleSyncHelper.waitOnPreviousGradleSyncFinish(gradleSystemListener);
        }

        LOG.info(String.format("[SYNC REQUEST %s] Sync has started%n", request.getId()));
        Stopwatch stopwatch = isStartup ? startupStopwatch : Stopwatch.createStarted();
        GradleSyncResult result = isStartup
            ? GradleSyncHelper.getStartupSyncResult(project, gradleSystemListener)
            : GradleSyncHelper.startManualSync(project, gradleSystemListener);
        LOG.info(String.format("[SYNC REQUEST %s] '%s'%n", request.getId(), result.getResult()));
        client.send(new IdeSyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS), result.getResult(), result.getErrorMessage()));
    }

    /**
     * This code is similar to one in com.intellij.ide.InvalidateCacheService in IntelliJ Community project,
     * it just does not make a dialog to restart IDE.
     */
    private void cleanupCache(IdeRequest request) {
        CachesInvalidator.EP_NAME.getExtensionList().forEach(it -> {
            try {
                it.invalidateCaches();
            } catch (Throwable t) {
                LOG.warn("Failed to invalidate caches with " + it.getClass().getName() + ". " + t.getMessage(), t);
            }
        });
        client.send(new IdeCacheCleanupCompleted(request.getId()));
    }
}
