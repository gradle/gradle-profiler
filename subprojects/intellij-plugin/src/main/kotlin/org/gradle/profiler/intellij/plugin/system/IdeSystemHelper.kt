package org.gradle.profiler.intellij.plugin.system

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx

/**
 * Common IDE helper utilities that work across both Android Studio and IntelliJ IDEA.
 */
object IdeSystemHelper {

    private const val WAIT_ON_PROCESS_SLEEP_TIME = 10L
    private const val WAIT_ON_STARTUP_SLEEP_TIME = 100L

    /** Waits for IDE indexing and any visible background tasks to finish. */
    @JvmStatic
    fun waitOnBackgroundProcessesFinish(project: Project) {
        // A dummy read action in smart mode blocks until all indexing is done.
        DumbService.getInstance(project).runReadActionInSmartMode {}
        val frame = WindowManagerEx.getInstanceEx().findFrameFor(project)
        val statusBar = frame?.statusBar as? StatusBarEx
        statusBar?.backgroundProcesses?.forEach { waitOnProgressIndicator(it.second) }
    }

    private fun waitOnProgressIndicator(progressIndicator: ProgressIndicator) {
        wait(WAIT_ON_PROCESS_SLEEP_TIME) { progressIndicator.isRunning }
    }

    @JvmStatic
    fun waitOnPostStartupActivities(project: Project) {
        wait(WAIT_ON_STARTUP_SLEEP_TIME) { !StartupManager.getInstance(project).postStartupActivityPassed() }
    }

    /** Waits for background processes to finish then exits the application. */
    @JvmStatic
    fun exit(project: Project) {
        waitOnBackgroundProcessesFinish(project)
        ApplicationManager.getApplication().exit(true, true, false)
    }

    private fun wait(sleepMillis: Long, whileCondition: () -> Boolean) {
        while (whileCondition()) {
            Thread.sleep(sleepMillis)
        }
    }
}
