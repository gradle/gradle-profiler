package org.gradle.profiler.fixtures.util

class RetryUtil {

    /**
     * Polls the given assertion until it succeeds, or the timeout expires.
     * <p>
     * By default, the assertion is polled every 100ms, and the timeout is 10 seconds.
     *
     * @param assertion The assertion to poll.
     */
    static void poll(
        double timeoutInSeconds = 10,
        double initialDelayInSeconds = 0,
        double pollIntervalInSeconds = 0.1,
        Runnable assertion
    ) {
        def start = monotonicClockMillis()
        Thread.sleep(toMillis(initialDelayInSeconds))
        def expiry = start + toMillis(timeoutInSeconds) // convert to ms
        long sleepTime = Math.max(100, toMillis(pollIntervalInSeconds))
        while (true) {
            try {
                assertion()
                return
            } catch (Throwable t) {
                def remaining = expiry - monotonicClockMillis()
                if (remaining <= 0) {
                    throw t
                }
                Thread.sleep(Math.min(remaining, sleepTime))
                sleepTime *= 1.2
            }
        }
    }

    static long monotonicClockMillis() {
        System.nanoTime() / 1000000L
    }

    static long toMillis(double seconds) {
        return (long) (seconds * 1000)
    }
}
