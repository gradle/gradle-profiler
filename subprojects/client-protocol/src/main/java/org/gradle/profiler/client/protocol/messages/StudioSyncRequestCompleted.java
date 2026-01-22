package org.gradle.profiler.client.protocol.messages;

public class StudioSyncRequestCompleted implements Message {


    public enum StudioSyncRequestResult {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    private final int id;
    private final long durationMillis;
    private final StudioSyncRequestResult result;
    private final String errorMessage;

    public StudioSyncRequestCompleted(int id, long durationMillis, StudioSyncRequestResult result, String errorMessage) {
        this.id = id;
        this.durationMillis = durationMillis;
        this.result = result;
        this.errorMessage = errorMessage;
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

    public String getErrorMessage() {
        return errorMessage;
    }
}
