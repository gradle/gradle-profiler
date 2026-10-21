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
import java.util.concurrent.CompletionException;
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
    public PendingBuild onNextBuildFinished(BuildFinishedAction action) {
        PendingBuild build = new PendingBuild(action);
        nextBuild.set(build);
        return build;
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

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    public interface BuildFinishedAction {
        void execute(String pid) throws IOException, InterruptedException;
    }

    /**
     * A build for which {@link #onNextBuildFinished(BuildFinishedAction)} was called.
     */
    public class PendingBuild {
        private final BuildFinishedAction action;
        private final CompletableFuture<Void> result = new CompletableFuture<>();

        private PendingBuild(BuildFinishedAction action) {
            this.action = action;
        }

        private void run(String pid) {
            try {
                action.execute(pid);
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        }

        /**
         * Completes handling of the build.
         *
         * <p>Call this once the Gradle client has exited. The build process waits for the action to complete before it returns
         * the build result to the client, so the action has already completed by then, unless the build process did not notify.</p>
         */
        public void completed() {
            nextBuild.compareAndSet(this, null);
            if (!result.isDone()) {
                throw new IllegalStateException("The build process did not notify that the build has finished.");
            }
            try {
                result.join();
            } catch (CompletionException e) {
                throw new RuntimeException("Could not handle the end of the build.", e.getCause());
            }
        }

        /**
         * Abandons handling of the build that did not complete, and adds any failure to handle its end to the build failure.
         */
        public void failed(Exception buildFailure) {
            nextBuild.compareAndSet(this, null);
            if (result.isCompletedExceptionally()) {
                try {
                    result.join();
                } catch (CompletionException e) {
                    buildFailure.addSuppressed(e.getCause());
                }
            }
        }
    }
}
