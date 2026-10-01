package org.gradle.profiler.perfetto.jfr;

/**
 * Allocates IDs from a single namespace for the whole trace.
 *
 * <p>
 * Perfetto expects tracks, counters, frames, and other interned entities to stay globally unique.
 */
public final class PerfettoIdProvider {
    private long nextId = 1L;

    public long nextId() {
        return nextId++;
    }
}
