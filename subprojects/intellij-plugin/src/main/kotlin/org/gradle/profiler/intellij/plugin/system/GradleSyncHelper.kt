package org.gradle.profiler.intellij.plugin.system

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted.IdeSyncRequestResult
import org.gradle.profiler.intellij.plugin.system.IdeSystemHelper.waitOnBackgroundProcessesFinish
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.TimeUnit

/**
 * Performs Gradle sync using the platform-agnostic ExternalSystemUtil API,
 * which works for both IntelliJ IDEA and Android Studio.
 */
object GradleSyncHelper {

    /**
     * Gets the result of the startup sync. If no sync has run yet, triggers one manually.
     */
    @JvmStatic
    fun getStartupSyncResult(project: Project, gradleSystemListener: GradleSystemListener): GradleSyncResult {
        if (!gradleSystemListener.hasSyncCompleted) {
            val result = startManualSync(project, gradleSystemListener)
            if (result.result == IdeSyncRequestResult.FAILED) {
                waitOnPreviousGradleSyncFinish(gradleSystemListener)
                waitOnBackgroundProcessesFinish(project)
            }
        }
        return buildSyncResult(gradleSystemListener.lastException)
    }

    /**
     * Triggers a Gradle sync and waits for it to complete.
     *
     * Uses [ProgressExecutionMode.IN_BACKGROUND_ASYNC] and waits via
     * [GradleSystemListener.awaitNextSyncCompletion] to reliably observe the
     * full result regardless of whether the sync executes synchronously or asynchronously.
     */
    @JvmStatic
    fun startManualSync(project: Project, gradleSystemListener: GradleSystemListener): GradleSyncResult {
        // Register the future BEFORE triggering the sync to avoid missing the onEnd() event.
        val nextSyncDone = gradleSystemListener.awaitNextSyncCompletion()

        ExternalSystemUtil.refreshProjects(
            ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
                .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
        )

        // Wait for the sync completion event (works for both sync and async execution).
        try {
            nextSyncDone.get(5, TimeUnit.MINUTES)
        } catch (_: Exception) {
            // Timeout or interruption — fall through with whatever state we have.
        }

        waitOnBackgroundProcessesFinish(project)
        return buildSyncResult(gradleSystemListener.lastException)
    }

    /** Waits for any currently in-progress Gradle sync to finish. */
    @JvmStatic
    fun waitOnPreviousGradleSyncFinish(gradleSystemListener: GradleSystemListener) {
        gradleSystemListener.waitForCurrentSyncToFinish()
    }

    private fun buildSyncResult(exception: Exception?): GradleSyncResult = if (exception == null) {
        GradleSyncResult(IdeSyncRequestResult.SUCCEEDED, "")
    } else {
        GradleSyncResult(IdeSyncRequestResult.FAILED, exception.message.orEmpty())
    }
}
