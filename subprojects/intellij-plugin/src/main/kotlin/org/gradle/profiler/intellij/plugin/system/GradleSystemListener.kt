package org.gradle.profiler.intellij.plugin.system

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Listens for Gradle sync (RESOLVE_PROJECT) task events and tracks their state,
 * enabling callers to wait for in-progress syncs to complete.
 */
class GradleSystemListener : ExternalSystemTaskNotificationListener {

    private val exceptionRef = AtomicReference<Exception>()
    private val syncHasCompletedFlag = AtomicBoolean(false)
    private val currentSyncFutureRef = AtomicReference<CompletableFuture<Void>>()
    /** Set before triggering a manual sync; completed when that sync's onEnd() fires. */
    private val nextSyncFutureRef = AtomicReference<CompletableFuture<Void>>()

    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
        if (isGradleSyncTask(id)) {
            exceptionRef.set(null)
            currentSyncFutureRef.set(CompletableFuture())
        }
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
        if (isGradleSyncTask(id)) {
            syncHasCompletedFlag.set(true)
            currentSyncFutureRef.get()?.complete(null)
            nextSyncFutureRef.getAndSet(null)?.complete(null)
        }
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        if (isGradleSyncTask(id)) {
            exceptionRef.set(exception)
            // onEnd() is always called after onFailure() and completes the futures
        }
    }

    val hasSyncCompleted: Boolean get() = syncHasCompletedFlag.get()

    /** Blocks until the currently in-progress sync finishes, or returns immediately if idle. */
    fun waitForCurrentSyncToFinish() {
        currentSyncFutureRef.get()?.takeUnless { it.isDone }?.join()
    }

    /**
     * Returns a [CompletableFuture] that completes when the NEXT sync's [onEnd] fires.
     * Must be called BEFORE starting the sync to avoid missing the event.
     */
    fun awaitNextSyncCompletion(): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        nextSyncFutureRef.set(future)
        return future
    }

    val lastException: Exception? get() = exceptionRef.get()

    companion object {
        private fun isGradleSyncTask(id: ExternalSystemTaskId): Boolean =
            GradleConstants.SYSTEM_ID == id.projectSystemId &&
                id.type == ExternalSystemTaskType.RESOLVE_PROJECT
    }
}
