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
    private val syncInProgressFlag = AtomicBoolean(false)
    private val syncHasCompletedFlag = AtomicBoolean(false)
    @Volatile private var currentSyncFuture: CompletableFuture<Void>? = null
    /** Set before triggering a manual sync; completed when that sync's onEnd() fires. */
    @Volatile private var nextSyncFuture: CompletableFuture<Void>? = null

    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
        if (isGradleSyncTask(id)) {
            exceptionRef.set(null)
            currentSyncFuture = CompletableFuture()
            syncInProgressFlag.set(true)
        }
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
        if (isGradleSyncTask(id)) {
            syncInProgressFlag.set(false)
            syncHasCompletedFlag.set(true)
            currentSyncFuture?.complete(null)
            nextSyncFuture?.let {
                nextSyncFuture = null
                it.complete(null)
            }
        }
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        if (isGradleSyncTask(id)) {
            exceptionRef.set(exception)
            // onEnd() is always called after onFailure() and completes the futures
        }
    }

    val isSyncInProgress: Boolean get() = syncInProgressFlag.get()

    val hasSyncCompleted: Boolean get() = syncHasCompletedFlag.get()

    /** Blocks until the currently in-progress sync finishes, or returns immediately if idle. */
    fun waitForCurrentSyncToFinish() {
        val future = currentSyncFuture
        if (future != null && !future.isDone) {
            future.join()
        }
    }

    /**
     * Returns a [CompletableFuture] that completes when the NEXT sync's [onEnd] fires.
     * Must be called BEFORE starting the sync to avoid missing the event.
     */
    fun awaitNextSyncCompletion(): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        nextSyncFuture = future
        return future
    }

    val lastException: Exception? get() = exceptionRef.get()

    companion object {
        private fun isGradleSyncTask(id: ExternalSystemTaskId): Boolean =
            GradleConstants.SYSTEM_ID == id.projectSystemId &&
                id.type == ExternalSystemTaskType.RESOLVE_PROJECT
    }
}
