package org.gradle.profiler.studio.plugin.system;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GradleSystemListener extends ExternalSystemTaskNotificationListenerAdapter {
    private final AtomicReference<Exception> exception = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> currentSyncFutureRef = new AtomicReference<>();
    private final AtomicBoolean syncCompleted = new AtomicBoolean(false);

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            exception.set(null);
            currentSyncFutureRef.set(new CompletableFuture<>());
        }
    }

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            syncCompleted.set(true);
            CompletableFuture<Void> future = currentSyncFutureRef.getAndSet(null);
            if (future != null) {
                future.complete(null);
            }
        }
    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            exception.set(e);
            syncCompleted.set(true);
            CompletableFuture<Void> future = currentSyncFutureRef.getAndSet(null);
            if (future != null) {
                future.complete(null);
            }
        }
    }

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId id) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) {
            syncCompleted.set(true);
            CompletableFuture<Void> future = currentSyncFutureRef.getAndSet(null);
            if (future != null) {
                future.complete(null);
            }
        }
    }

    @Nullable
    public Exception getLastException() {
        return exception.get();
    }

    public boolean hasSyncCompleted() {
        return syncCompleted.get();
    }

    /**
     * Returns a future that completes when the next sync finishes.
     * Must be called BEFORE triggering the sync.
     */
    public CompletableFuture<Void> awaitNextSyncCompletion() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        currentSyncFutureRef.set(future);
        return future;
    }

    /**
     * Waits for any currently running sync to finish.
     */
    public void waitForCurrentSyncToFinish() {
        CompletableFuture<Void> future = currentSyncFutureRef.get();
        if (future != null) {
            future.join();
        }
    }
}
