package org.gradle.profiler.studio.plugin.system;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.concurrent.atomic.AtomicReference;

public class GradleSystemListener extends ExternalSystemTaskNotificationListenerAdapter {
    private final AtomicReference<Exception> exception = new AtomicReference<>();

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            exception.set(null);
        }
    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            exception.set(e);
        }
    }

    @Nullable
    public Exception getLastException() {
        return exception.get();
    }
}
