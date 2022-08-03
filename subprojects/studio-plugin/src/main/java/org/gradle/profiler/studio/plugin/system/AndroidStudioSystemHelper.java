package org.gradle.profiler.studio.plugin.system;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.studio.plugin.system.GradleProfilerGradleSyncListener.GradleSyncResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_SYNC_ACTION;

public class AndroidStudioSystemHelper {

    private static final long WAIT_ON_PROCESS_SLEEP_TIME = 10;

    /**
     * Does a Gradle sync.
     */
    public static GradleSyncResult doGradleSync(Project project) {
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener();
        try {
            requestProjectSync(project, TRIGGER_USER_SYNC_ACTION, syncListener);
        } catch (Exception e) {
            e.printStackTrace();
            syncListener.syncFailed(project, e.getMessage());
        }
        return syncListener.waitAndGetResult();
    }

    /**
     * We use reflection since Android Studio Electric Eel adds a modified android plugin at runtime on classpath
     */
    private static void requestProjectSync(Project project, GradleSyncStats.Trigger trigger, GradleSyncListener listener) {
        try {
            Object syncInvoker = ApplicationManager.getApplication().getService(GradleSyncInvoker.class);
            Method method = syncInvoker.getClass().getMethod(
                "requestProjectSync",
                Project.class,
                GradleSyncInvoker.Request.class,
                GradleSyncListener.class
            );
            method.invoke(syncInvoker, project, new GradleSyncInvoker.Request(trigger), listener);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Registers a listener that waits on next gradle sync if it's in progress.
     */
    public static void waitOnPreviousGradleSyncFinish(Project project) {
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener();
        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(GradleSyncState.GRADLE_SYNC_TOPIC, syncListener);
        if (!isSyncInProgress(project)) {
            // Sync was actually not in progress,
            // just acknowledge the listener, so it won't wait forever.
            syncListener.syncSkipped(project);
        }
        try {
            syncListener.waitAndGetResult();
        } finally {
            connection.disconnect();
        }
    }

    /**
     * We use reflection since Android Studio Electric Eel adds a modified android plugin at runtime on classpath
     */
    private static boolean isSyncInProgress(Project project) {
        try {
            Object syncState = project.getService(GradleSyncState.class);
            Method method = syncState.getClass().getMethod("isSyncInProgress");
            return (boolean) method.invoke(syncState);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Wait on Android Studio indexing and similar background tasks to finish.
     *
     * It seems there is no better way to do it atm.
     */
    public static void waitOnBackgroundProcessesFinish(Project project) {
        IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
        StatusBarEx statusBar = frame == null ? null : (StatusBarEx) frame.getStatusBar();
        if (statusBar != null) {
            statusBar.getBackgroundProcesses().forEach(it -> waitOn(it.getSecond()));
        }
    }

    @SuppressWarnings("BusyWait")
    private static void waitOn(ProgressIndicator progressIndicator) {
        while (progressIndicator.isRunning()) {
            try {
                Thread.sleep(WAIT_ON_PROCESS_SLEEP_TIME);
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
