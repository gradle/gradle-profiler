package org.gradle.profiler.studio.plugin;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.ide.impl.TrustChangeNotifier;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.SyncRequestCompleted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.RequestType.EXIT;

public class GradleProfilerManagerListener implements ProjectManagerListener {

    @Override
    public void projectOpened(com.intellij.openapi.project.Project project) {
        System.out.println("Project opened");
        TrustedProjects.setTrusted(project, true);
        System.out.println("Is project trusted: " + TrustedProjects.isTrusted(project));
        Client.INSTANCE.connect(Integer.parseInt(System.getProperty("gradle.profiler.port")));
        System.out.println("Connected to port: " + System.getProperty("gradle.profiler.port"));
        Client.INSTANCE.listenAsync(it -> {
            boolean isRunning = true;
            StudioRequest request = it.receiveStudioRequest(Duration.ofDays(1));
            GradleSyncState syncState = GradleSyncState.getInstance(project);
            while (request.getType() != EXIT) {
                if (syncState.isSyncInProgress()) {
                    // It seems like someone else triggered the sync,
                    // this can happen at fresh startup

                }
                waitWhileGradleSyncIsInProgress(syncState);
                System.out.println("Received sync request with id: " + request.getId());
                long startTimeNanos = System.nanoTime();
                syncProject(project, startTimeNanos, it, request);
                waitWhileGradleSyncIsInProgress(syncState);
                long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
                System.out.println("Duration millis: " + durationMillis);
                it.send(new SyncRequestCompleted(request.getId(), durationMillis));
                System.out.println("Sent sync request completed");
                request = it.receiveStudioRequest(Duration.ofDays(1));
            }
            ApplicationManager.getApplication().exit(true, true, false);
        });
    }

//                    connection.subscribe(GradleSyncState.GRADLE_SYNC_TOPIC, new GradleSyncListener() {
//        @Override
//        public void syncSucceeded(@NotNull Project project) {
//            long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
//            System.out.println("Duration millis: " + durationMillis);
//            it.send(new SyncRequestCompleted(request.getId(), durationMillis));
////                            connection.disconnect();
//        }
//
//        @Override
//        public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
//            long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
//            System.out.println("Duration millis: " + durationMillis);
//            it.send(new SyncRequestCompleted(request.getId(), durationMillis));
////                            connection.disconnect();
//        }
//
//        @Override
//        public void syncSkipped(@NotNull Project project) {
//            long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
//            System.out.println("Duration millis: " + durationMillis);
//            it.send(new SyncRequestCompleted(request.getId(), durationMillis));
////                            connection.disconnect();
//        }
//    });

    private void waitWhileGradleSyncIsInProgress(GradleSyncState state) {
        while (state.isSyncInProgress()) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private void syncProject(Project project, long startTimeNanos, Client client, StudioRequest request) {
        requireNonNull(project, "No project is opened");
        ExternalSystemUtil.refreshProject(
            project,
            GradleConstants.SYSTEM_ID,
            "/Users/asodja/workspace/santa-tracker-android",
            new ExternalProjectRefreshCallback() {

                @Override
                public void onSuccess(@Nullable DataNode<ProjectData> externalProject) {
                    long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
                    System.out.println("Duration millis: " + durationMillis);
//                    client.send(new SyncRequestCompleted(request.getId(), durationMillis));
//                    try {
//                        Thread.sleep(3000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }

                @Override
                public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
                    long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
//                    System.out.println("Duration millis: " + durationMillis);
//                    client.send(new SyncRequestCompleted(request.getId(), durationMillis));
//                    try {
//                        Thread.sleep(3000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }
            },
            false,
            ProgressExecutionMode.MODAL_SYNC,
            true
        );
    }

}
