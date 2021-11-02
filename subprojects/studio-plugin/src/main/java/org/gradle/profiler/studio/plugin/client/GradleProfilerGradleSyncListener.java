package org.gradle.profiler.studio.plugin.client;

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.openapi.project.Project;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Listens to a SINGLE Gradle sync event and returns result of it.
 */
public class GradleProfilerGradleSyncListener implements GradleSyncListener {

    private final BlockingQueue<GradleSyncResult> results;
    private final StudioRequest request;

    public GradleProfilerGradleSyncListener(StudioRequest request) {
        this.request = request;
        this.results = new ArrayBlockingQueue<>(1);
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
        if (results.isEmpty()) {
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
