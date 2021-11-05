package org.gradle.profiler.client.protocol.messages;

public class GradleInvocationCompleted implements Message {
    private final int id;
    private final long durationMillis;

    public GradleInvocationCompleted(int id, long durationMillis) {
        this.id = id;
        this.durationMillis = durationMillis;
    }

    @Override
    public String toString() {
        return "gradle invocation completed " + id + " in " + durationMillis + "ms";
    }

    public int getId() {
        return id;
    }

    public long getDurationMillis() {
        return durationMillis;
    }
}
