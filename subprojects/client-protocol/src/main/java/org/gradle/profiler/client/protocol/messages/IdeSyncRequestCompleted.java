package org.gradle.profiler.client.protocol.messages;

public class IdeSyncRequestCompleted implements Message {

    public enum IdeSyncRequestResult {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    private final int id;
    private final long durationMillis;
    private final IdeSyncRequestResult result;
    private final String errorMessage;

    public IdeSyncRequestCompleted(int id, long durationMillis, IdeSyncRequestResult result, String errorMessage) {
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

    public IdeSyncRequestResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
