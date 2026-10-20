package org.gradle.profiler.profilers.support;

/**
 * Sent by the profiler in reply to {@link BuildFinished}, once it has stopped recording the build process.
 */
public final class RecordingStopped implements ProfilerMessage {
    @Override
    public String toString() {
        return "RecordingStopped";
    }
}
