package org.gradle.profiler.studio.plugin.system;

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SKIPPED;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SKIPPED_OUT_OF_DATE;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.UNKNOWN;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

public class AndroidStudioSystemHelper {

    private static final long WAIT_ON_PROCESS_SLEEP_TIME = 10;
    private static final long WAIT_ON_STARTUP_SLEEP_TIME = 100;

    /**
     * Gets the result of startup sync by checking if there was already any sync done before. Otherwise, it starts a new sync.
     */
    public static GradleSyncResult getStartupSyncResult(Project project, GradleSystemListener gradleSystemListener) {
        ProjectSystemSyncManager.SyncResult lastSyncResult = ProjectSystemUtil.getSyncManager(project).getLastSyncResult();
        if (lastSyncResult == UNKNOWN || lastSyncResult == SKIPPED) {
            // Sync was not run before, we need to run it manually
            GradleSyncResult result = startManualSync(project, gradleSystemListener);
            if (result.getResult() == StudioSyncRequestResult.FAILED) {
                // If it fails, it might fail because another sync just started a millisecond before we could start it
                waitOnPreviousGradleSyncFinish(project);
                waitOnBackgroundProcessesFinish(project);
            }
        }
        return convertToGradleSyncResult(ProjectSystemUtil.getSyncManager(project).getLastSyncResult(), gradleSystemListener.getLastException());
    }

    /**
     * Starts a manual sync and returns a result.
     */
    public static GradleSyncResult startManualSync(Project project, GradleSystemListener gradleSystemListener) {
        GradleSyncResult result = doGradleSync(project, gradleSystemListener);
        waitOnBackgroundProcessesFinish(project);
        return result;
    }

    private static GradleSyncResult doGradleSync(Project project, GradleSystemListener gradleSystemListener) {
        try {
            ProjectSystemSyncManager.SyncResult syncResult = ProjectSystemUtil.getSyncManager(project).syncProject(SyncReason.USER_REQUEST).get();
            return convertToGradleSyncResult(syncResult, gradleSystemListener.getLastException());
        } catch (InterruptedException | ExecutionException e) {
            return new GradleSyncResult(StudioSyncRequestResult.FAILED, e.getMessage());
        }
    }

    private static GradleSyncResult convertToGradleSyncResult(ProjectSystemSyncManager.SyncResult syncResult, @Nullable Throwable throwable) {
        if ((syncResult == SKIPPED || syncResult == SKIPPED_OUT_OF_DATE)) {
            return new GradleSyncResult(StudioSyncRequestResult.SKIPPED, "");
        } else if (syncResult.isSuccessful()) {
            return new GradleSyncResult(StudioSyncRequestResult.SUCCEEDED, "");
        } else  {
            String error = throwable != null ? throwable.getMessage() : "Unknown error";
            return new GradleSyncResult(StudioSyncRequestResult.FAILED, Strings.nullToEmpty(error));
        }
    }

    /**
     * Registers a listener that waits on next gradle sync if it's in progress.
     */
    public static void waitOnPreviousGradleSyncFinish(Project project) {
        if (ProjectSystemUtil.getSyncManager(project).isSyncInProgress()) {
            MessageBusConnection connection = project.getMessageBus().connect();
            CompletableFuture<Void> future = new CompletableFuture<>();
            connection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, (ProjectSystemSyncManager.SyncResultListener) syncResult -> future.complete(null));
            future.join();
        }
    }

    /**
     * Wait on Android Studio indexing and similar background tasks to finish.
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
