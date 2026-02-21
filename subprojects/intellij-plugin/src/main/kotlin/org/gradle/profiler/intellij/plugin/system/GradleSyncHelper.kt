package org.gradle.profiler.intellij.plugin.system

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted.IdeSyncRequestResult
import org.gradle.profiler.intellij.plugin.system.IdeSystemHelper.waitOnBackgroundProcessesFinish
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Performs Gradle sync using the platform-agnostic ExternalSystemUtil API,
 * which works for both IntelliJ IDEA and Android Studio.
 */
object GradleSyncHelper {

    /**
     * Gets the result of the startup sync. If no sync has run yet, triggers one manually.
     */
    @JvmStatic
    fun getStartupSyncResult(project: Project, gradleSystemListener: GradleSystemListener, ideDataImportListener: IdeDataImportListener): GradleSyncResult {
        if (!gradleSystemListener.hasSyncCompleted) {
            val result = startManualSync(project, gradleSystemListener, ideDataImportListener)
            if (result.result == IdeSyncRequestResult.FAILED) {
                waitOnPreviousGradleSyncFinish(gradleSystemListener)
                waitOnPreviousImportToFinish(ideDataImportListener)
                waitOnBackgroundProcessesFinish(project)
            }
        }
        return buildSyncResult(gradleSystemListener.lastException ?: ideDataImportListener.lastException)
    }

    /**
     * Triggers a Gradle sync and waits for both the Gradle phase and the
     * subsequent IDE data import phase to complete.
     *
     * Uses [ProgressExecutionMode.IN_BACKGROUND_ASYNC] and waits via
     * [GradleSystemListener.awaitNextSyncCompletion] and
     * [IdeDataImportListener.awaitNextImportCompletion] to reliably observe the
     * full result regardless of whether the sync executes synchronously or asynchronously.
     */
    @JvmStatic
    fun startManualSync(project: Project, gradleSystemListener: GradleSystemListener, ideDataImportListener: IdeDataImportListener): GradleSyncResult {
        // Register futures BEFORE triggering the sync to avoid missing onEnd()/onImportFinished() events.
        val nextSyncDone = gradleSystemListener.awaitNextSyncCompletion()
        val nextImportDone = ideDataImportListener.awaitNextImportCompletion()

        ExternalSystemUtil.refreshProjects(
            ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
                .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
        )

        nextSyncDone.join()
        nextImportDone.join()

        waitOnBackgroundProcessesFinish(project)
        return buildSyncResult(gradleSystemListener.lastException ?: ideDataImportListener.lastException)
    }

    /** Waits for any currently in-progress Gradle sync to finish. */
    @JvmStatic
    fun waitOnPreviousGradleSyncFinish(gradleSystemListener: GradleSystemListener) {
        gradleSystemListener.waitForCurrentSyncToFinish()
    }

    /** Waits for any currently in-progress IDE data import to finish. */
    @JvmStatic
    fun waitOnPreviousImportToFinish(ideDataImportListener: IdeDataImportListener) {
        ideDataImportListener.waitForCurrentImportToFinish()
    }

    private fun buildSyncResult(exception: Throwable?): GradleSyncResult = if (exception == null) {
        GradleSyncResult(IdeSyncRequestResult.SUCCEEDED, "")
    } else {
        GradleSyncResult(IdeSyncRequestResult.FAILED, exception.message.orEmpty())
    }
}
