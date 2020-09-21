package org.gradle.profiler.studio.instrumented;

import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.SyncCompleted;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;

public class RecordingResultHandler implements ResultHandler<Object> {
    private final ResultHandler<Object> delegate;
    private final int id;
    private final long startTimeNanos;

    public RecordingResultHandler(ResultHandler<Object> delegate, int id, long startTimeNanos) {
        this.delegate = delegate;
        this.id = id;
        this.startTimeNanos = startTimeNanos;
    }

    @Override
    public void onComplete(Object result) {
        System.out.println("OPERATION COMPLETE: " + result);
        sendEvent();
        delegate.onComplete(result);
    }

    @Override
    public void onFailure(GradleConnectionException failure) {
        System.out.println("OPERATION FAILED: " + failure);
        sendEvent();
        delegate.onFailure(failure);
    }

    private void sendEvent() {
        long durationMillis = (System.nanoTime() - startTimeNanos) / 1000000;
        Client.INSTANCE.send(new SyncCompleted(id, durationMillis));
    }
}
