package org.gradle.profiler.studio.plugin.system;

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.FAILED;
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.SKIPPED;
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.SUCCEEDED;

/**
 * Listens to a SINGLE Gradle sync event and returns result of it.
 */
public class GradleProfilerGradleSyncListener implements GradleSyncListener {

    private final BlockingQueue<GradleSyncResult> results;
    private final MessageBusConnection connection;

    public GradleProfilerGradleSyncListener(MessageBusConnection connection) {
        this.results = new ArrayBlockingQueue<>(1);
        this.connection = connection;
    }

    @Override
    public synchronized void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        setResult(new GradleSyncResult(FAILED, errorMessage));
    }

    @Override
    public synchronized void syncSkipped(@NotNull Project project) {
        setResult(new GradleSyncResult(SKIPPED, ""));

    }

    @Override
    public synchronized void syncSucceeded(@NotNull Project project) {
        setResult(new GradleSyncResult(SUCCEEDED, ""));
    }

    private void setResult(GradleSyncResult result) {
        if (results.isEmpty()) {
            results.add(result);
        }
    }

    public GradleSyncResult waitAndGetResult() {
        try {
            return results.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            connection.disconnect();
        }
    }

    public static class GradleSyncResult {

        private final StudioSyncRequestResult status;
        private final String errorMessage;

        public GradleSyncResult(StudioSyncRequestResult status, String errorMessage) {
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public StudioSyncRequestResult getResult() {
            return status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

}
