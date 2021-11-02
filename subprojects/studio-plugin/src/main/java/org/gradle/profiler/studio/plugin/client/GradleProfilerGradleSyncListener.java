package org.gradle.profiler.studio.plugin.client;

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens to a SINGLE Gradle sync event and returns result of it.
 */
public class GradleProfilerGradleSyncListener implements GradleSyncListener {

    private final MessageBusConnection connection;
    private final BlockingQueue<GradleSyncResult> results;
    private final StudioRequest request;
    private final AtomicBoolean isConnected;

    public GradleProfilerGradleSyncListener(StudioRequest request, Project project) {
        this.request = request;
        this.connection = GradleSyncState.subscribe(project, this);
        this.results = new ArrayBlockingQueue<>(1);
        this.isConnected = new AtomicBoolean(false);
    }

    @Override
    public synchronized void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        System.out.println("Sync failed for: " + request.getId() + ": " + errorMessage);
        setResult(new GradleSyncResult());
    }

    @Override
    public synchronized void syncSkipped(@NotNull Project project) {
        System.out.println("Sync skipped for: " + request.getId());
        setResult(new GradleSyncResult());
    }

    @Override
    public synchronized void syncSucceeded(@NotNull Project project) {
        System.out.println("Sync succeeded for: " + request.getId());
        setResult(new GradleSyncResult());
    }

    private void setResult(GradleSyncResult result) {
        if (isConnected.getAndSet(false)) {
            connection.disconnect();
            results.add(result);
        }
    }

    public GradleSyncResult waitAndGetResult() {
        try {
            return results.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class GradleSyncResult {

    }

}
