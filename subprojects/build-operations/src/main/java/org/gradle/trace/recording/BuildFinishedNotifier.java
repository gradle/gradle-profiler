package org.gradle.trace.recording;

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.profiler.profilers.support.BuildFinished;
import org.gradle.profiler.profilers.support.ProfilerConnection;
import org.gradle.profiler.profilers.support.RecordingStopped;
import org.gradle.util.GradleVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Notifies the profiler when the build has finished, and waits until the profiler has stopped recording this process.
 *
 * <p>This runs before the build result is returned to the Gradle client, so the profiler can stop recording a
 * single-use build process while it is still alive, and the recording does not include the shutdown of the process.</p>
 *
 * <p>See {@code org.gradle.profiler.instrument.BuildFinishedInstrumentation} for the profiler side.</p>
 */
@SuppressWarnings("unused")
public class BuildFinishedNotifier {
    private static final int CONNECT_ATTEMPTS = 5;

    public static void register(GradleInternal gradle, int port) {
        String pid = gradle.getServices().get(ProcessEnvironment.class).getPid().toString();
        if (GradleVersion.current().compareTo(GradleVersion.version("6.1")) >= 0) {
            // Build services are only available since Gradle 6.1, so they are referenced from a separate class
            BuildFinishedNotifierService.register(gradle, port, pid);
        } else {
            gradle.buildFinished(result -> notifyBuildFinished(port, pid));
        }
    }

    static void notifyBuildFinished(int port, String pid) {
        try (ProfilerConnection connection = new ProfilerConnection(connect(port))) {
            connection.send(new BuildFinished(pid));
            // Stopping the recording can take a while, e.g. to write a large snapshot
            connection.receive(RecordingStopped.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Connecting can fail spuriously in a profiled process, e.g. when a profiler signal interrupts the connection.
     * The profiler ignores connections that close without a message, so connecting again is safe.
     */
    private static Socket connect(int port) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                return new Socket(InetAddress.getLoopbackAddress(), port);
            } catch (IOException e) {
                if (attempt == CONNECT_ATTEMPTS) {
                    throw new IOException("Could not connect to the profiler on port " + port + ".", e);
                }
                System.err.println("Could not connect to the profiler on port " + port + ": " + e + ". Connecting again.");
            }
        }
    }
}
