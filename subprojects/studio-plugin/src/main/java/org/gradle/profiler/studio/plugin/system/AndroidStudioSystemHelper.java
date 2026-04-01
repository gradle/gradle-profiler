package org.gradle.profiler.studio.plugin.system;

import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class AndroidStudioSystemHelper {

    private static final long WAIT_ON_PROCESS_SLEEP_TIME = 10;
    private static final long WAIT_ON_STARTUP_SLEEP_TIME = 100;

    /**
     * Gets the result of startup sync by checking if there was already any sync done before. Otherwise, it starts a new sync.
     */
    public static GradleSyncResult getStartupSyncResult(Project project, GradleSystemListener gradleSystemListener) {
        if (!gradleSystemListener.hasAnySyncCompleted()) {
            // Sync was not run before, we need to run it manually
            GradleSyncResult result = startManualSync(project, gradleSystemListener);
            if (result.getResult() == StudioSyncRequestResult.FAILED) {
                // If it fails, it might fail because another sync just started a millisecond before we could start it
                waitOnPreviousGradleSyncFinish(gradleSystemListener);
                waitOnBackgroundProcessesFinish(project);
            }
        }
        return buildSyncResult(gradleSystemListener);
    }

    /**
     * Starts a manual sync and returns a result.
     */
    public static GradleSyncResult startManualSync(Project project, GradleSystemListener gradleSystemListener) {
        CompletableFuture<Void> nextSyncDone = gradleSystemListener.awaitNextSyncCompletion();
        ExternalSystemUtil.refreshProjects(
            new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
        );
        nextSyncDone.join();
        waitOnBackgroundProcessesFinish(project);
        return buildSyncResult(gradleSystemListener);
    }

    private static GradleSyncResult buildSyncResult(GradleSystemListener gradleSystemListener) {
        Exception exception = gradleSystemListener.getLastException();
        if (exception != null) {
            return new GradleSyncResult(StudioSyncRequestResult.FAILED, Strings.nullToEmpty(exception.getMessage()));
        }
        return new GradleSyncResult(StudioSyncRequestResult.SUCCEEDED, "");
    }

    /**
     * Waits for any in-progress Gradle sync to finish.
     */
    public static void waitOnPreviousGradleSyncFinish(GradleSystemListener gradleSystemListener) {
        gradleSystemListener.waitForCurrentSyncToFinish();
    }

    /**
     * Wait on IDE indexing and similar background tasks to finish.
     * <p>
     * It seems there is no better way to do it atm.
     */
    public static void waitOnBackgroundProcessesFinish(Project project) {
        // Run a dummy read action just so we wait on all indexing done
        DumbService.getInstance(project).runReadActionInSmartMode(() -> {});
        IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
        StatusBarEx statusBar = frame == null ? null : (StatusBarEx) frame.getStatusBar();
        if (statusBar != null) {
            statusBar.getBackgroundProcesses().forEach(it -> waitOnProgressIndicator(it.getSecond()));
        }
    }

    private static void waitOnProgressIndicator(ProgressIndicator progressIndicator) {
        wait(WAIT_ON_PROCESS_SLEEP_TIME, progressIndicator::isRunning);
    }

    public static void waitOnPostStartupActivities(Project project) {
        wait(WAIT_ON_STARTUP_SLEEP_TIME, () -> !StartupManager.getInstance(project).postStartupActivityPassed());
    }

    @SuppressWarnings("BusyWait")
    private static void wait(long sleepMillis, Supplier<Boolean> whileCondition) {
        while (whileCondition.get()) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Exit the application.
     */
    public static void exit(Project project) {
        waitOnBackgroundProcessesFinish(project);
        ApplicationManager.getApplication().exit(true, true, false);
    }
}
