package org.gradle.profiler.studio.plugin.client;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.SyncRequestCompleted;
import org.gradle.profiler.studio.plugin.client.GradleProfilerGradleSyncListener.GradleSyncResult;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.RequestType.EXIT;

public class GradleProfilerClient {

    private static final String PROFILER_PORT_PROPERTY = "gradle.profiler.port";

    private final Object lock = new Object();

    public void connectToProfilerAsync(Project project) {
        if (System.getProperty(PROFILER_PORT_PROPERTY) == null) {
            return;
        }

        int port = Integer.parseInt(System.getProperty(PROFILER_PORT_PROPERTY));
        Client.INSTANCE.connect(port);
        System.out.println("Connected to port: " + System.getProperty(PROFILER_PORT_PROPERTY));

        GradleSyncState syncState = GradleSyncState.getInstance(project);
        Client.INSTANCE.listenAsync(it -> {
            StudioRequest request;
            while ((request = it.receiveStudioRequest(Duration.ofDays(1))).getType() != EXIT) {
                handleGradleProfilerRequest(request, project, syncState);
            }
            ApplicationManager.getApplication().exit(true, true, false);
        });
    }

    private void handleGradleProfilerRequest(StudioRequest request, Project project, GradleSyncState syncState) {
        switch (request.getType()) {
            case SYNC:
                handleSyncRequest(request, project, syncState);
                break;
            case EXIT:
                throw new IllegalArgumentException("Type: '" + request.getType() + "' should not be handled in 'handleGradleProfilerRequest()'.");
            default:
                throw new IllegalArgumentException("Unknown request type: '" + request.getType() + "'.");
        }
    }

    private void handleSyncRequest(StudioRequest request, Project project, GradleSyncState syncState) {
        System.out.println("Received sync request with id: " + request.getId());
        long startTimeNanos = System.nanoTime();
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener(request, project);
        if (!syncState.isSyncInProgress()) {
            // It seems like someone else triggered the sync,
            // this can happen at fresh startup
            syncProject(project, startTimeNanos, request);
        }
        System.out.println("WAITING ON RESULT");
        GradleSyncResult result = syncListener.waitAndGetResult();
        System.out.println("GOT RESULT");
        System.out.println(result);
        long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
        Client.INSTANCE.send(new SyncRequestCompleted(request.getId(), durationMillis));
    }

    private void syncProject(Project project, long startTimeNanos, StudioRequest request) {
        requireNonNull(project, "No project is opened");
        ExternalSystemUtil.refreshProject(
            project,
            GradleConstants.SYSTEM_ID,
            "/Users/asodja/workspace/santa-tracker-android",
            new ExternalProjectRefreshCallback() {},
            false,
            ProgressExecutionMode.IN_BACKGROUND_ASYNC,
            true
        );
    }

}
