package org.gradle.profiler.studio.plugin;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.SyncRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

public class GradleProfilerManagerListener implements ProjectManagerListener, ApplicationInitializedListener {

    @Override
    public void projectOpened(com.intellij.openapi.project.Project project) {
        System.out.println("Project opened");
        Client.INSTANCE.connect(Integer.parseInt(System.getProperty("gradle.profiler.port")));
        Client.INSTANCE.listenAsync(it -> {
            boolean isRunning = true;
            while (isRunning) {
                SyncRequest request = it.receiveSyncRequest(Duration.ofDays(1));
                System.out.println("Received sync request with id: " + request.getId());
                long startTimeNanos = System.nanoTime();
                syncProject(project);
                long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
                System.out.println("Duration millis: " + durationMillis);
                it.send();
            }

        });
        System.out.println("Connected to port: " + System.getProperty("gradle.profiler.port"));
    }

    private void syncProject(Project project) {
        requireNonNull(project, "No project is opened");
        ExternalSystemUtil.refreshProject(
            project,
            GradleConstants.SYSTEM_ID,
            "/Users/asodja/workspace/gradle-profiler",
            new ExternalProjectRefreshCallback() {

                @Override
                public void onSuccess(@Nullable DataNode<ProjectData> externalProject) {
                    System.out.println("SUCCESS");
                }

                @Override
                public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
                    System.out.println("FAILURE");
                }
            },
            false,
            ProgressExecutionMode.MODAL_SYNC,
            true
        );
    }

    @Override
    public void componentsInitialized() {
        System.out.println("Project componentsInitialized");
    }
}
