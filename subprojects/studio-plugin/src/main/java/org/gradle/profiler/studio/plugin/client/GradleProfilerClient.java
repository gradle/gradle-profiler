package org.gradle.profiler.studio.plugin.client;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.common.base.Stopwatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.studio.plugin.client.GradleProfilerGradleSyncListener.GradleSyncResult;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_SYNC_ACTION;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT;

public class GradleProfilerClient {

    private static final long WAIT_ON_PROCESS_SLEEP_TIME = 10;
    private static final String PROFILER_PORT_PROPERTY = "gradle.profiler.port";

    public void connectToProfilerAsync(Project project) {
        if (System.getProperty(PROFILER_PORT_PROPERTY) == null) {
            return;
        }

        int port = Integer.parseInt(System.getProperty(PROFILER_PORT_PROPERTY));
        Client.INSTANCE.connect(port);
        System.out.println("Connected to port: " + System.getProperty(PROFILER_PORT_PROPERTY));

        Client.INSTANCE.listenAsync(it -> {
            StudioRequest request;
            while ((request = it.receiveStudioRequest(Duration.ofDays(1))).getType() != EXIT) {
                handleGradleProfilerRequest(request, project);
            }
            ApplicationManager.getApplication().exit(true, true, false);
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
        System.out.println("Received sync request with id: " + request.getId());

        // In some cases sync could happen before we trigger it,
        // for example when we open a project for the first time.
        waitOnAndroidStudioBuildInProgress(project);
        maybeWaitOnPreviousSyncFinish(project);

        Stopwatch stopwatch = Stopwatch.createStarted();
        System.out.printf("[SYNC REQUEST %s] Sync has started%n", request.getId());
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener();
        GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_USER_SYNC_ACTION, syncListener);
        GradleSyncResult result = syncListener.waitAndGetResult();
        waitOnAndroidStudioBuildInProgress(project);
        System.out.printf("[SYNC REQUEST %s] '%s': '%s'%n", request.getId(), result.getResult(), result.getErrorMessage().isEmpty() ? "no message" : result.getErrorMessage());
        Client.INSTANCE.send(new StudioSyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS), result.getResult()));
    }

    private void waitOnAndroidStudioBuildInProgress(Project project) {
        IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
        StatusBarEx statusBar = frame == null ? null : (StatusBarEx) frame.getStatusBar();
        if (statusBar != null) {
            statusBar.getBackgroundProcesses().forEach(it -> waitOn(it.getSecond()));
        }
    }

    @SuppressWarnings("BusyWait")
    private void waitOn(ProgressIndicator progressIndicator) {
        while (progressIndicator.isRunning()) {
            try {
                Thread.sleep(WAIT_ON_PROCESS_SLEEP_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void maybeWaitOnPreviousSyncFinish(Project project) {
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener();
        MessageBusConnection connection = GradleSyncState.subscribe(project, syncListener);
        if (!GradleSyncState.getInstance(project).isSyncInProgress()) {
            // Sync was actually not in progress,
            // just acknowledge the listener, so it won't wait forever.
            syncListener.syncSkipped(project);
        }
        try {
            syncListener.waitAndGetResult();
        } finally {
            connection.disconnect();
        }
    }

}
