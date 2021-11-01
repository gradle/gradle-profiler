package org.gradle.profiler.studio.plugin;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

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
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            System.out.println("On end: " + GradleConstants.SYSTEM_ID);
        }
    }

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            System.out.println("On onSuccess: " + GradleConstants.SYSTEM_ID);
        }
    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            System.out.println("On onFailure: " + GradleConstants.SYSTEM_ID);
        }
    }

    @Override
    public void beforeCancel(@NotNull ExternalSystemTaskId id) {

    }

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId id) {

    }
}
