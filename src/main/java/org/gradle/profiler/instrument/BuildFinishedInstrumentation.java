package org.gradle.profiler.instrument;

import org.gradle.profiler.profilers.support.BuildFinished;
import org.gradle.profiler.profilers.support.ProfilerConnection;
import org.gradle.profiler.profilers.support.RecordingStopped;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lets the profiler act at the end of each build, while the build process is still alive.
 *
 * <p>The build process notifies the profiler when the build has finished and waits for the profiler to reply,
 * before it returns the build result to the Gradle client. See {@code org.gradle.trace.recording.BuildFinishedNotifier}
 * for the build side.</p>
 */
public class BuildFinishedInstrumentation extends GradleInstrumentation implements Closeable {
    private final ServerSocket serverSocket;
    private final AtomicReference<PendingBuild> nextBuild = new AtomicReference<>();
    private PendingBuild currentBuild;

    public BuildFinishedInstrumentation() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread listener = new Thread(this::listen, "build finished listener");
        listener.setDaemon(true);
        listener.start();
    }

    @Override
    protected void generateInitScriptBody(PrintWriter writer) {
        writer.println("org.gradle.trace.recording.BuildFinishedNotifier.register(gradle, " + serverSocket.getLocalPort() + ")");
    }

    /**
     * Runs the given action when the next build finishes, with the PID of its build process.
     */
    public void onNextBuildFinished(BuildFinishedAction action) {
        currentBuild = new PendingBuild(action);
        nextBuild.set(currentBuild);
    }

    private void listen() {
        while (!serverSocket.isClosed()) {
            // The build process connects only once the build has finished
            try (ProfilerConnection connection = new ProfilerConnection(serverSocket.accept())) {
                handle(connection);
            } catch (IOException | RuntimeException e) {
                // The connection failed or sent an unexpected message, or the server socket was closed.
                // Keep listening, so later build processes do not wait forever.
            }
        }
    }

    private void handle(ProfilerConnection connection) throws IOException {
        BuildFinished buildFinished;
        try {
            buildFinished = connection.receive(BuildFinished.class);
        } catch (EOFException e) {
            // The build process failed to connect and connects again
            return;
        }
        PendingBuild build = nextBuild.getAndSet(null);
        try {
            if (build != null) {
                build.run(buildFinished.getPid());
            }
        } finally {
            // Always reply, so the build process does not wait forever
            connection.send(new RecordingStopped());
        }
    }

    /**
     * Completes handling of a build for which {@link #onNextBuildFinished(BuildFinishedAction)} was called.
     *
     * <p>Call this once the Gradle client has exited. The build process notifies the profiler before it returns the build result to
     * the client, so the action has already run by then, unless the build process could not notify.</p>
     */
    public void buildCompleted() throws InterruptedException {
        PendingBuild build = currentBuild;
        currentBuild = null;
        try {
            build.result.get(1, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not handle the end of the build.", e.getCause());
        } catch (TimeoutException e) {
            nextBuild.compareAndSet(build, null);
            throw new IllegalStateException("The build process did not notify that the build has finished.");
        }
    }

    /**
     * Abandons handling of a build that did not complete, and adds any failure to handle its end to the build failure.
     */
    public void buildFailed(Exception buildFailure) {
        PendingBuild build = currentBuild;
        currentBuild = null;
        if (build == null) {
            return;
        }
        nextBuild.compareAndSet(build, null);
        if (build.result.isCompletedExceptionally()) {
            try {
                build.result.join();
            } catch (Exception e) {
                buildFailure.addSuppressed(e.getCause() != null ? e.getCause() : e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    public interface BuildFinishedAction {
        void execute(String pid) throws IOException, InterruptedException;
    }

    private static class PendingBuild {
        private final BuildFinishedAction action;
        private final CompletableFuture<Void> result = new CompletableFuture<>();

        PendingBuild(BuildFinishedAction action) {
            this.action = action;
        }

        void run(String pid) {
            try {
                action.execute(pid);
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        }
    }
}
