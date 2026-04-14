package org.gradle.profiler.ide.plugin.system;

import org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted.IdeSyncRequestResult;

public class GradleSyncResult {

    private final IdeSyncRequestResult status;
    private final String errorMessage;

    public GradleSyncResult(IdeSyncRequestResult status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public IdeSyncRequestResult getResult() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
