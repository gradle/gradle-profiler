package org.gradle.profiler;

import java.time.Duration;

class Timer {
    final long start;

    public Timer() {
        start = System.nanoTime();
    }

    public Duration elapsed() {
        long end = System.nanoTime();
        return Duration.ofNanos(end - start);
    }
}
