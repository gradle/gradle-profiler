package org.gradle.profiler.client.protocol.messages;

import org.gradle.profiler.client.protocol.Message;

public class SyncRequestCompleted extends Message {

    private final int id;
    private final long durationMillis;

    public SyncRequestCompleted(int id, long durationMillis) {
        this.id = id;
        this.durationMillis = durationMillis;
    }

    @Override
    public String toString() {
        return "sync completed " + id + " in " + durationMillis + "ms";
    }

    public int getId() {
        return id;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

}
