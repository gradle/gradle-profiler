package org.gradle.profiler.studio.plugin.system;

import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult;

public class GradleSyncResult {

    private final StudioSyncRequestResult status;
    private final String errorMessage;

    public GradleSyncResult(StudioSyncRequestResult status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public StudioSyncRequestResult getResult() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
