package org.gradle.profiler.studio.plugin.client;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.common.base.Stopwatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.SyncRequestCompleted;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_SYNC_ACTION;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.RequestType.EXIT;

public class GradleProfilerClient {

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

        // In some cases sync can happen before we trigger it,
        // for example when we open a project for the first time.
        maybeWaitOnPreviousSyncFinish(project);

        Stopwatch stopwatch = Stopwatch.createStarted();
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener(request);
        GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_USER_SYNC_ACTION, syncListener);
        syncListener.waitAndGetResult();
        Client.INSTANCE.send(new SyncRequestCompleted(request.getId(), stopwatch.elapsed(TimeUnit.MILLISECONDS)));
    }

    private void maybeWaitOnPreviousSyncFinish(Project project) {
        BlockingQueue<String> results = new LinkedBlockingQueue<>();
        MessageBusConnection connection = GradleSyncState.subscribe(project, new GradleSyncListener() {
            @Override
            public synchronized void syncSucceeded(@NotNull Project project) {
                results.add("done");
            }

            @Override
            public synchronized void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
                results.add("done");
            }

            @Override
            public synchronized void syncSkipped(@NotNull Project project) {
                results.add("done");
            }
        });
        if (!GradleSyncState.getInstance(project).isSyncInProgress()) {
            // Sync was actually not in progress,
            // just acknowledge the listener, so it won't wait forever.
            results.add("done");
        }
        try {
            results.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            connection.disconnect();
        }
    }

}
