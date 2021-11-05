package org.gradle.profiler.studio.plugin.system;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.studio.plugin.system.GradleProfilerGradleSyncListener.GradleSyncResult;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_SYNC_ACTION;

public class AndroidStudioSystemHelper {

    public static final String INTEGRATION_TEST_PROPERTY = "gradle.profiler.is.integration.test";
    private static final long WAIT_ON_PROCESS_SLEEP_TIME = 10;

    /**
     * Does a Gradle sync.
     *
     * Note: In case of integration tests, it will be a no-op, since currently Gradle sync stops the application and test do not finish.
     */
    public GradleSyncResult doGradleSync(Project project) {
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener();
        if (Boolean.getBoolean(INTEGRATION_TEST_PROPERTY)) {
            syncListener.syncSucceeded(project);
        } else {
            GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_USER_SYNC_ACTION, syncListener);
        }
        return syncListener.waitAndGetResult();
    }

    /**
     * Registers a listener that waits on next gradle sync if it's in progress.
     */
    public void waitOnPreviousGradleSyncFinish(Project project) {
        GradleProfilerGradleSyncListener syncListener = new GradleProfilerGradleSyncListener();
        MessageBusConnection connection = GradleSyncState.subscribe(project, syncListener);
        if (!GradleSyncState.getInstance(project).isSyncInProgress()) {
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
     * Wait on Android Studio indexing and similar background tasks to finish.
     *
     * It seems there is no better way to do it atm.
     */
    public void waitOnBackgroundProcessesFinish(Project project) {
        IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
        StatusBarEx statusBar = frame == null ? null : (StatusBarEx) frame.getStatusBar();
        if (statusBar != null) {
            statusBar.getBackgroundProcesses().forEach(it -> waitOn(it.getSecond()));
        }
    }

    @SuppressWarnings("BusyWait")
    private void waitOn(ProgressIndicator progressIndicator) {
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
     *
     * In case of integration tests, intellij-gradle-plugin will close the application for us.
     */
    public void exit() {
        if (!Boolean.getBoolean(INTEGRATION_TEST_PROPERTY)) {
            ApplicationManager.getApplication().exit(true, true, false);
        }
    }

}
