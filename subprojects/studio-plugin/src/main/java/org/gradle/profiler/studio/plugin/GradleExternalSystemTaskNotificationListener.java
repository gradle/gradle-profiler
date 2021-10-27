package org.gradle.profiler.studio.plugin;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;

public class GradleExternalSystemTaskNotificationListener implements ExternalSystemTaskNotificationListener {

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {

    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {

    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {

    }

    @Override
    public void beforeCancel(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId id) {

    }
}
