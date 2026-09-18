package org.gradle.profiler.perfetto.jfr;

import jdk.jfr.consumer.RecordedThread;

/**
 * Normalized thread identity used as the bridge between JFR thread metadata and Perfetto tracks.
 *
 * <p>It prefers stable OS thread IDs when available and falls back to Java thread IDs only when necessary.
 */
public record ThreadIdentity(long trackKey, int tid, String name) {
    public static ThreadIdentity from(RecordedThread thread) {
        if (thread == null) {
            return null;
        }

        long rawId = thread.getOSThreadId() > 0 ? thread.getOSThreadId() : thread.getJavaThreadId();
        if (rawId <= 0 || rawId > Integer.MAX_VALUE) {
            return null;
        }

        String javaName = thread.getJavaName();
        if (javaName != null && !javaName.isBlank()) {
            return new ThreadIdentity(rawId, (int) rawId, javaName);
        }
        String osName = thread.getOSName();
        if (osName != null && !osName.isBlank()) {
            return new ThreadIdentity(rawId, (int) rawId, osName);
        }
        return new ThreadIdentity(rawId, (int) rawId, "Thread " + rawId);
    }
}
