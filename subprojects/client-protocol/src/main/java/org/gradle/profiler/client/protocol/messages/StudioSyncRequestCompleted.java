package org.gradle.profiler.client.protocol.messages;

import org.gradle.profiler.client.protocol.Message;

public class StudioSyncRequestCompleted extends Message {

    public enum StudioSyncRequestResult {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    private final int id;
    private final long durationMillis;
    private final StudioSyncRequestResult result;

    public StudioSyncRequestCompleted(int id, long durationMillis, StudioSyncRequestResult result) {
        this.id = id;
        this.durationMillis = durationMillis;
        this.result = result;
    }

    @Override
    public String toString() {
        return "sync completed " + id + " in " + durationMillis + "ms with result: " + result;
    }

    public int getId() {
        return id;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public StudioSyncRequestResult getResult() {
        return result;
    }
}
