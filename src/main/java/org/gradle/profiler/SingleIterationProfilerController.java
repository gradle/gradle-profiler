package org.gradle.profiler;

import java.io.IOException;

public abstract class SingleIterationProfilerController implements ProfilerController {

    private boolean recordedBefore = false;

    @Override
    public final void startSession() throws IOException, InterruptedException {

    }

    @Override
    public final void startRecording() throws IOException, InterruptedException {
        if (recordedBefore) {
            throw new RuntimeException("Recording multiple iterations with cleanup runs in between is not supported by " + getName());
        }

        doStartRecording();
    }

    protected abstract void doStartRecording() throws IOException, InterruptedException;

    @Override
    public final void stopRecording() throws IOException, InterruptedException {
        recordedBefore = true;
    }

    @Override
    public void stopSession() throws IOException, InterruptedException {

    }

    public abstract String getName();
}
