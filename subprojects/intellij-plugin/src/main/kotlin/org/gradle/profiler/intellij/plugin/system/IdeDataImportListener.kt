package org.gradle.profiler.intellij.plugin.system

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * Listens for IDE project data import events and tracks their state,
 * enabling callers to wait for in-progress imports to complete.
 *
 * This covers the IDE-side half of a Gradle sync: after the Gradle build
 * finishes, IntelliJ processes the data model and sets up modules/facets.
 *
 * Subscribe via [com.intellij.openapi.project.Project.getMessageBus].
 */
class IdeDataImportListener : ProjectDataImportListener {

    private val exceptionRef = AtomicReference<Throwable>()
    private val currentImportFutureRef = AtomicReference<CompletableFuture<Void>>()
    /** Set before triggering a sync; completed when [onImportFinished] or [onImportFailed] fires. */
    private val nextImportFutureRef = AtomicReference<CompletableFuture<Void>>()

    override fun onImportStarted(projectPath: String?) {
        exceptionRef.set(null)
        currentImportFutureRef.set(CompletableFuture())
    }

    override fun onImportFinished(projectPath: String?) {
        completeImport()
    }

    // The no-arg overload is the deprecated fallback; keep it so legacy callers still signal completion.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onImportFailed(projectPath: String?) {
        completeImport()
    }

    override fun onImportFailed(projectPath: String?, t: Throwable) {
        exceptionRef.set(t)
        completeImport()
    }

    private fun completeImport() {
        currentImportFutureRef.get()?.complete(null)
        nextImportFutureRef.getAndSet(null)?.complete(null)
    }

    val lastException: Throwable? get() = exceptionRef.get()

    /** Blocks until any currently in-progress import finishes, or returns immediately if idle. */
    fun waitForCurrentImportToFinish() {
        currentImportFutureRef.get()?.takeUnless { it.isDone }?.join()
    }

    /**
     * Returns a [CompletableFuture] that completes when the NEXT import's
     * [onImportFinished] or [onImportFailed] fires.
     * Must be called BEFORE starting the sync to avoid missing the event.
     */
    fun awaitNextImportCompletion(): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        nextImportFutureRef.set(future)
        return future
    }
}
