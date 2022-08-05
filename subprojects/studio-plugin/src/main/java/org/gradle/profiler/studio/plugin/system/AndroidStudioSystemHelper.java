package org.gradle.profiler.studio.plugin.system;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.studio.plugin.system.GradleProfilerGradleSyncListener.GradleSyncResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

public class AndroidStudioSystemHelper {

    private static final long WAIT_ON_PROCESS_SLEEP_TIME = 10;
    private static final long WAIT_ON_STARTUP_SLEEP_TIME = 100;

    /**
     * Does a Gradle sync.
     */
    public static GradleSyncResult doGradleSync(Project project) {
        GradleProfilerGradleSyncListener syncListener = subscribeToGradleSync(project);
        try {
            // We could get sync result from the `Future` returned by the syncProject(),
            // but it doesn't return error message so we rather listen to GRADLE_SYNC_TOPIC to get the sync result
            ProjectSystemUtil.getSyncManager(project).syncProject(ProjectSystemSyncManager.SyncReason.USER_REQUEST).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return syncListener.waitAndGetResult();
    }

    /**
     * Registers a listener that waits on next gradle sync if it's in progress.
     */
    public static void waitOnPreviousGradleSyncFinish(Project project) {
        MessageBusConnection connection = project.getMessageBus().connect();
        CompletableFuture<String> future = new CompletableFuture<>();
        connection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, syncResult -> future.complete("Done"));
        if (!ProjectSystemUtil.getSyncManager(project).isSyncInProgress()) {
            // Sync was actually not in progress,
            // just acknowledge the listener, so it won't wait forever.
            future.complete("Done");
        }
        try {
            future.join();
        } finally {
            connection.disconnect();
        }
    }

    private static GradleProfilerGradleSyncListener subscribeToGradleSync(Project project) {
        MessageBusConnection connection = project.getMessageBus().connect();
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener(connection);
        connection.subscribe(GradleSyncState.GRADLE_SYNC_TOPIC, syncListener);
        return syncListener;
    }

    /**
     * Wait on Android Studio indexing and similar background tasks to finish.
     * <p>
     * It seems there is no better way to do it atm.
     */
    public static void waitOnBackgroundProcessesFinish(Project project) {
        IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
        StatusBarEx statusBar = frame == null ? null : (StatusBarEx) frame.getStatusBar();
        if (statusBar != null) {
            statusBar.getBackgroundProcesses().forEach(it -> waitOnProgressIndicator(it.getSecond()));
        }
    }

    private static void waitOnProgressIndicator(ProgressIndicator progressIndicator) {
        wait(progressIndicator::isRunning, WAIT_ON_PROCESS_SLEEP_TIME);
    }

    public static void waitOnPostStartupActivities(Project project) {
        wait(() -> !StartupManager.getInstance(project).postStartupActivityPassed(), WAIT_ON_STARTUP_SLEEP_TIME);
    }

    @SuppressWarnings("BusyWait")
    private static void wait(Supplier<Boolean> whileCondition, long sleepMillis) {
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
