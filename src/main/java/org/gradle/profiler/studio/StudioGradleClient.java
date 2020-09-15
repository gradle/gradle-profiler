package org.gradle.profiler.studio;

import org.gradle.profiler.GradleClient;

import java.time.Duration;

public class StudioGradleClient implements GradleClient {
    @Override
    public void close() {
    }

    public Duration sync() {
        System.out.println("* WARNING: Android Studio sync is not implemented yet.");
        return Duration.ZERO;
    }
}
